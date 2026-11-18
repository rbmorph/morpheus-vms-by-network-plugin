package com.custom.morpheus.networkvms

import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Permission
import com.morpheusdata.model.User
import com.morpheusdata.views.HandlebarsRenderer
import com.morpheusdata.web.PluginController

class NetworkVmsTabPlugin extends Plugin {

	@Override
	String getCode() {
		'network-vms-tab'
	}

	@Override
	void initialize() {
		this.setName('Network VMs Tab')
		this.setDescription('Adds a VMs tab to network details listing the virtual machines on that network')
		NetworkVmsTabProvider tab = new NetworkVmsTabProvider(this, morpheus)
		this.registerProviders(tab)
		List<PluginController> controllers = [new NetworkVmsController(this, morpheus, tab)]
		this.setControllers(controllers)
		// A plugin with controllers and no renderer of its own gets its templates added to Morpheus's
		// shared renderer. That shared template list is fixed-size, so the add throws
		// UnsupportedOperationException and the upload fails with "Failed to parse plugin file".
		// Giving the plugin its own renderer skips that step.
		this.setRenderer(new HandlebarsRenderer('renderer', this.getClassLoader()))
	}

	/**
	 * Controls who sees the VMs tab. Listed with the Infrastructure permissions on user and tenant
	 * roles under Administration > Roles. None hides the tab; Read or Full shows it.
	 */
	@Override
	List<Permission> getPermissions() {
		[Permission.build(PERMISSION_NAME, NetworkVmsController.PERMISSION_CODE, PERMISSION_CATEGORY,
			[Permission.AccessType.none, Permission.AccessType.read, Permission.AccessType.full])]
	}

	static final String PERMISSION_NAME = 'Infrastructure: Network VMs Tab'
	static final String PERMISSION_CATEGORY = 'infrastructure'

	/** True when the user's roles grant the tab permission at Read or Full. */
	static boolean canView(User user) {
		String access = user?.permissions?.get(NetworkVmsController.PERMISSION_CODE)
		access == Permission.AccessType.read.toString() || access == Permission.AccessType.full.toString()
	}

	@Override
	void onDestroy() {
	}
}
