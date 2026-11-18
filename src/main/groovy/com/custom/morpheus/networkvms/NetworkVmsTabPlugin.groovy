package com.custom.morpheus.networkvms

import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Permission

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
		this.controllers.add(new NetworkVmsController(this, morpheus, tab))
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
