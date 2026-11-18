package com.custom.morpheus.networkvms

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Permission
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.web.PluginController
import com.morpheusdata.web.Route

/**
 * Serves the VM table for the VMs tab, at /plugin/networkVmsTab/vms?networkId=ID. The tab fetches
 * it the first time it's shown, so opening a network page doesn't wait on the VM lookup.
 */
class NetworkVmsController implements PluginController {

	static final String PERMISSION_CODE = 'network-vms-tab'
	static final String VMS_URL = '/networkVmsTab/vms'

	Plugin plugin
	MorpheusContext morpheusContext
	private final NetworkVmsTabProvider tab

	NetworkVmsController(Plugin plugin, MorpheusContext context, NetworkVmsTabProvider tab) {
		this.plugin = plugin
		this.morpheusContext = context
		this.tab = tab
	}

	@Override
	String getCode() {
		'network-vms-tab-controller'
	}

	@Override
	String getName() {
		'Network VMs Tab Controller'
	}

	@Override
	MorpheusContext getMorpheus() {
		morpheusContext
	}

	@Override
	Plugin getPlugin() {
		plugin
	}

	// A user needs either of these, at any access level. The VM list itself is still limited to
	// the user's own tenant unless they're in the Master tenant.
	@Override
	List<Route> getRoutes() {
		[
			Route.build(VMS_URL, 'vms', [
				new Permission('Infrastructure: Network', 'infrastructure-network', [Permission.AccessType.read, Permission.AccessType.full]),
				new Permission('Network VMs Tab', PERMISSION_CODE, [Permission.AccessType.full])
			])
		]
	}

	def vms(ViewModel<Map> model) {
		tab.renderVms(networkIdFrom(model), model.user)
	}

	private static Long networkIdFrom(ViewModel<Map> model) {
		def value = model.request?.getParameter('networkId')
		if(value == null && model.object instanceof Map) {
			value = ((Map) model.object).networkId
		}
		if(value instanceof Collection || value instanceof Object[]) {
			value = value ? value[0] : null
		}
		String text = value?.toString()?.trim()
		text?.isLong() ? text.toLong() : null
	}
}
