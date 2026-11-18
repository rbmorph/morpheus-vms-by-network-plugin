package com.custom.morpheus.networkvms

import com.morpheusdata.core.AbstractNetworkTabProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.model.Account
import com.morpheusdata.model.Cloud
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.ComputeServerInterface
import com.morpheusdata.model.Network
import com.morpheusdata.model.User
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import groovy.util.logging.Slf4j

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Adds a "VMs" tab to a network's detail page listing the virtual machines with an interface on it.
 *
 * "Virtual machines" means what Infrastructure > Compute > Virtual Machines lists: servers whose
 * server type is a guest VM, leaving out hypervisor and Docker hosts.
 *
 * VMs are found by loading the servers in the network's cloud with their interfaces and keeping
 * those with an interface on this network.
 *
 * Tenant scoping: renderTemplate() isn't told who is looking, so show() remembers the viewer's
 * tenant for the render that follows on the same request. Master tenant users see every tenant's
 * VMs; everyone else sees only their own tenant's. If the viewer is unknown, no VMs are shown.
 */
@Slf4j
class NetworkVmsTabProvider extends AbstractNetworkTabProvider {

	private static final int BATCH_SIZE = 500
	private static final BigDecimal BYTES_PER_GB = 1024G * 1024G * 1024G
	private static final ThreadLocal<Map> VIEWER = new ThreadLocal<>()

	Plugin plugin
	MorpheusContext morpheusContext

	NetworkVmsTabProvider(Plugin plugin, MorpheusContext context) {
		this.plugin = plugin
		this.morpheusContext = context
	}

	@Override
	MorpheusContext getMorpheus() {
		morpheusContext
	}

	@Override
	Plugin getPlugin() {
		plugin
	}

	@Override
	String getCode() {
		'network-vms-tab'
	}

	// The tab's label on the network detail page.
	@Override
	String getName() {
		'VMs'
	}

	@Override
	Boolean show(Network network, User user, Account account) {
		Account viewer = account ?: user?.account
		if(viewer?.id == null) {
			VIEWER.remove()
		} else {
			VIEWER.set([accountId: viewer.id, master: viewer.masterAccount == true])
		}
		true
	}

	@Override
	HTMLResponse renderTemplate(Network network) {
		Map viewer = VIEWER.get()
		VIEWER.remove()
		Map data
		try {
			data = viewer ? collect(network, viewer) : [error: "Couldn't tell which tenant you're in, so no VMs are shown."]
		} catch(Exception e) {
			log.error("Network VMs tab failed for network ${network?.id}: ${e.message}", e)
			data = [error: "Couldn't load the VMs on this network: ${e.message}".toString()]
		}
		ViewModel<Map> model = new ViewModel<>()
		model.object = data
		getRenderer().renderTemplate('hbs/networkVms', model)
	}

	private Map collect(Network network, Map viewer) {
		Long networkId = network.id
		Map<Long, ComputeServer> servers = [:]
		Map<Long, List<ComputeServerInterface>> attached = [:]
		serversFor(network).each { ComputeServer server ->
			if(!isVirtualMachine(server)) {
				return
			}
			if(!viewer.master && server.account?.id != viewer.accountId) {
				return
			}
			List<ComputeServerInterface> interfaces = server.interfaces?.findAll { it.network?.id == networkId }
			if(interfaces) {
				servers[server.id] = server
				attached[server.id] = interfaces
			}
		}

		List<ComputeServer> vms = (servers.values() as List<ComputeServer>).sort { (it.displayName ?: it.name)?.toLowerCase() }
		Map<Long, List<Map>> instances = instancesByServer(vms*.id)
		Map<Long, String> tenants = tenantNames(vms.findResults { it.account?.id })

		List<Map> rows = vms.collect { ComputeServer vm ->
			List<ComputeServerInterface> interfaces = attached[vm.id]
			[
				id: vm.id,
				name: vm.displayName ?: vm.name,
				hostname: vm.hostname,
				instances: instances[vm.id],
				tenant: tenants[vm.account?.id],
				powerState: vm.powerState?.toString(),
				poweredOn: vm.powerState == ComputeServer.PowerState.on,
				status: vm.status,
				ipAddresses: interfaces.collectMany { [it.ipAddress, it.ipv6Address] }.findAll().unique().join(', '),
				macAddresses: interfaces.findResults { it.macAddress }.unique().join(', '),
				interfaces: interfaces.collect { it.name ?: '(unnamed)' }.join(', '),
				os: vm.serverOs?.name ?: vm.osType,
				cores: vm.maxCores,
				memoryGb: vm.maxMemory ? (vm.maxMemory / BYTES_PER_GB).setScale(1, BigDecimal.ROUND_HALF_UP) : null
			]
		}
		[
			vms: rows,
			vmCount: rows.size(),
			poweredOnCount: rows.count { it.poweredOn },
			showTenant: viewer.master,
			notice: viewer.master ? null : "Showing only your tenant's VMs."
		]
	}

	/**
	 * Servers that could be on the network, with interfaces, server type and OS loaded. That's the
	 * network's cloud, or every cloud when the network isn't tied to one.
	 */
	private List<ComputeServer> serversFor(Network network) {
		List<Long> cloudIds = network.cloud?.id != null ? [network.cloud.id] : morpheus.services.cloud.list(new DataQuery()).collect { Cloud cloud -> cloud.id }
		List<Long> ids = cloudIds.collectMany { Long cloudId ->
			morpheus.async.computeServer.listIdentityProjections(cloudId, (String) null).map { it.id }.toList().blockingGet()
		}.unique()
		List<ComputeServer> servers = []
		ids.collate(BATCH_SIZE).each { List<Long> batch ->
			servers.addAll(morpheus.services.computeServer.list(new DataQuery()
				.withFilter('id', 'in', batch)
				.withJoins('interfaces', 'computeServerType', 'serverOs')))
		}
		servers
	}

	/** Matches Infrastructure > Compute > Virtual Machines: guest VMs, not hypervisor or Docker hosts. */
	private static boolean isVirtualMachine(ComputeServer server) {
		def type = server.computeServerType
		type?.guestVm == true && type.vmHypervisor != true && type.containerHypervisor != true
	}

	/** Instances by server ID, through the server's workloads. */
	private Map<Long, List<Map>> instancesByServer(List<Long> serverIds) {
		Map<Long, List<Map>> found = [:]
		serverIds.collate(BATCH_SIZE).each { List<Long> ids ->
			morpheus.services.workload.list(new DataQuery().withFilter('server.id', 'in', ids).withJoin('instance')).each { workload ->
				Long serverId = workload.server?.id
				def instance = workload.instance
				if(serverId == null || instance?.id == null) {
					return
				}
				List<Map> list = found.computeIfAbsent(serverId) { [] }
				if(!list.any { it.id == instance.id }) {
					list << [id: instance.id, name: instance.displayName ?: instance.name]
				}
			}
		}
		found
	}

	/** Tenant names by ID. The plugin API has no tenant service, so this reads the account table. */
	private Map<Long, String> tenantNames(Collection<Long> ids) {
		Map<Long, String> names = [:]
		List<Long> unique = ids.findAll { it != null }.unique() as List<Long>
		if(!unique) {
			return names
		}
		def reports = morpheus.async.report
		Connection connection = reports.getReadOnlyDatabaseConnection().blockingGet()
		try {
			unique.collate(BATCH_SIZE).each { List<Long> batch ->
				PreparedStatement statement = connection.prepareStatement("SELECT id, name FROM account WHERE id IN (${batch.collect { '?' }.join(',')})".toString())
				try {
					batch.eachWithIndex { Long id, int i -> statement.setLong(i + 1, id) }
					ResultSet results = statement.executeQuery()
					while(results.next()) {
						names[results.getLong('id')] = results.getString('name')
					}
				} finally {
					statement.close()
				}
			}
		} finally {
			reports.releaseDatabaseConnection(connection).blockingAwait()
		}
		names
	}
}
