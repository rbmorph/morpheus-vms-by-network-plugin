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

	/** Lets admins grant access to the tab's data to roles without the built-in Network permission. */
	@Override
	List<Permission> getPermissions() {
		[new Permission('Network VMs Tab', NetworkVmsController.PERMISSION_CODE, [Permission.AccessType.full])]
	}

	@Override
	void onDestroy() {
	}
}
