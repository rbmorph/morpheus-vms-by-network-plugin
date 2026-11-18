package com.custom.morpheus.networkvms

import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Permission
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
		// Replace the list rather than adding to it: on Morpheus 9.x the default list is read-only,
		// and adding to it fails with UnsupportedOperationException when the plugin loads.
		List<PluginController> controllers = [new NetworkVmsController(this, morpheus, tab)]
		this.setControllers(controllers)
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
