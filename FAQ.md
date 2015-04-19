**Which devices are supported?**

This app has been tested on the ADP1 (T-Mobile G1) and Google Nexus One.

For the ADP1: A modified kernel including all needed modifications to enable usb-tethering is available **[here](http://code.google.com/p/android-wired-tether/downloads/list?q=Kernel)**. A tutorial on how to replace the kernel on the ADP1 (running Android 1.6/Donut) can be found [here](http://code.google.com/p/android-wifi-tether/wiki/ADP16KernelUpdate) - make sure to apply usbtether-kernel!

For the Nexus One: Please see below for instructions.

**Why do I get the message "Unsupported Kernel" on startup?**

This means that the Linux kernel on your device does not have the features (CONFIG\_NETFILTER/CONFIG\_IP\_NF\_IPTABLES and/or RNDIS) required for tethering. If you have an ADP G1, please read (or [ADP16KernelUpdate](ADP16KernelUpdate.md) if you're using Donut), otherwise you will need to find a firmware/kernel with these features. The developers of Wired Tether are unable to help with other types of devices (but donations of hardware/etc would help ;)

**My operating system is asking me for drivers?**

This program has been tested with Linux and Microsoft Windows clients. The client needs to support RNDIS. Windows Vista/7 comes with **[RDNIS](http://www.microsoft.com/whdc/device/network/ndis/rmndis.mspx)**-support out of the box. Windows XP requires to download and install a driver. I recommend using the **[HTC Sync](http://www.htc.com/hero/m/files/downloads/HTCsync.zip)** (for the HTC Hero) which contains the required driver. The driver is located under C:\Program Files\HTC\HTC Driver\Driver Files\XP\_x86 in case it's not automatically installed when plugging in.


**Where can I find patches for that RNDIS-kernel feature? I want to compile my own kernel!**

Zinx Verituse has ported the essential parts from the official HTC Hero kernel.
Patches can be extracted from [here](http://github.com/cyanogen/cm-kernel/commit/91588ab04d1466bfc85a3d0fe48adc3c68034fd3) and [here](http://github.com/cyanogen/cm-kernel/commit/d01c8caf593c0dc4f73e1b5f8d5dd789559e0976).

**How do I update the kernel on the Nexus One?**

Please see the instructions on the [Wireless Tether Wiki](http://code.google.com/p/android-wifi-tether/wiki/NexusOneKernelUpdate). Yes, the kernel you download there will work.