package com.custom.morpheus.networkvms

import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Permission
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

	/** Grants access to the tab's VM list. Admins give it to roles under Administration > Roles. */
	@Override
	List<Permission> getPermissions() {
		[new Permission('Network VMs Tab', NetworkVmsController.PERMISSION_CODE, [Permission.AccessType.full])]
	}

	@Override
	void onDestroy() {
	}
}
