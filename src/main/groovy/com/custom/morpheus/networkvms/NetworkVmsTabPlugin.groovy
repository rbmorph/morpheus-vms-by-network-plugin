package com.custom.morpheus.networkvms

import com.morpheusdata.core.Plugin

class NetworkVmsTabPlugin extends Plugin {

	@Override
	String getCode() {
		'network-vms-tab'
	}

	@Override
	void initialize() {
		this.setName('Network VMs Tab')
		this.setDescription('Adds a VMs tab to network details listing the virtual machines on that network')
		this.registerProviders(new NetworkVmsTabProvider(this, morpheus))
	}

	@Override
	void onDestroy() {
	}
}
