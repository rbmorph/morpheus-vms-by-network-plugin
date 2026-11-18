# Network VMs Tab

A Morpheus plugin that adds a **VMs** tab to each network's detail page. The tab lists the virtual
machines with an interface on that network.

"Virtual machines" means what **Infrastructure > Compute > Virtual Machines** lists: servers whose
type is a guest VM. Hypervisor hosts and Docker hosts are left out.

## Build

Needs JDK 11 or newer. Internet access is required to download dependencies.

    ./gradlew jar

The plugin is written to `build/libs/network-vms-tab-1.0.0.jar`.

It is built against plugin API 1.1.6 and declares Morpheus 7.0.0 as its minimum. To target a
newer appliance, change `morphPluginApiVersion` in `gradle.properties`.

## Automatic builds and releases

Every push to `main` and every pull request builds the jar on GitHub Actions. Download it from the
run's **Artifacts** section on the Actions tab.

To publish a release, push a version tag such as `v1.0.0` or `1.0.0`, or create a release on GitHub
with a new tag:

    git tag v1.0.0
    git push origin v1.0.0

The jar is built with the tag's version and attached to a GitHub release for that tag. The tag must
point at a commit that includes `.github/workflows/build.yml`.

## Install

1. Go to **Administration > Integrations > Plugins** and upload the jar.
2. Open any network under **Infrastructure > Network** and select the **VMs** tab.

## What the tab shows

The VM list loads the first time you open the tab, not with the network page, so a network with many
VMs doesn't slow the page down. The table stays loaded until you leave the page.

A count of VMs on the network and how many are powered on, then one row per VM: name (linked to
the VM), instance (linked), tenant, power state, status, the VM's IP and MAC addresses on
this network, the interface names, OS, cores and memory.

A VM with several interfaces on the same network gets one row, with its addresses comma-separated.

## Who sees what

- **Master tenant users** see every tenant's VMs on the network, with a Tenant column.
- **Everyone else** sees only their own tenant's VMs.

### Permission

The plugin adds an **Infrastructure: Network VMs Tab** permission to roles under
**Administration > Roles**, alongside the other Infrastructure permissions on user and tenant roles.

- **None:** the tab is hidden.
- **Read** or **Full:** the tab is shown.

A new permission starts out ungranted, so after installing, set it on the roles that should see the
tab, including your admin roles. A subtenant user's role can't grant more than their tenant role
allows, so set it on the tenant role too.

## What to check on first run

Compare a few networks against the VMs you expect, covering each cloud type you use.

- **Tab stays on "Loading VMs…":** open the browser's developer console on the network page. A
  Content-Security-Policy error means Morpheus blocked the tab's script; report it with your
  Morpheus version.
- **Tab doesn't appear:** the user's roles don't grant **Infrastructure: Network VMs Tab** at Read
  or Full. Log out and back in after changing a role.
- **Permission is under Uncategorized instead of Infrastructure:** the appliance didn't accept the
  `infrastructure` category name. Check the `subCategory` of an existing Infrastructure permission
  in `GET /api/roles/:id` and set `PERMISSION_CATEGORY` in `NetworkVmsTabPlugin.groovy` to match.
- **Tab says the server returned 403 or 404:** open `/plugin/networkVmsTab/vms?networkId=<id>`
  directly while logged in to see the response.
- **Fewer VMs than expected:** check `morpheus-ui` logs for errors from `NetworkVmsTabProvider`,
  then compare with `GET /api/servers/:id` for a missing VM and look at its
  `interfaces[].network.id` and `computeServerType.guestVm`.
- **Tab is slow to fill in on large clouds:** the page itself isn't held up, but the table loads
  every server in the network's cloud to check its interfaces, each time the page is opened.

## Known limits

- Only interfaces linked to the network itself are matched. A VM attached through a subnet record,
  with no network on the interface, isn't listed.
- For a network not tied to a cloud, every cloud's servers are checked, which is slower.
- Users see all of their tenant's VMs on the network, regardless of group access.
