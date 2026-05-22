package io.wasmcloud.endive.sysinfo;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public class SystemInfo {
    private final OperatingSystemMXBean osMxBean;

    public SystemInfo() {
        this.osMxBean = ManagementFactory.getOperatingSystemMXBean();
    }

    public String osArch() {
        return osMxBean.getArch();
    }

    public String osName() {
        return osMxBean.getName();
    }

    public String osVersion() {
        return osMxBean.getVersion();
    }

    public float systemCpuUsage() {
        if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            return (float) sunBean.getCpuLoad() * 100;
        }
        return 0f;
    }

    public long systemMemoryTotal() {
        if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            return sunBean.getTotalMemorySize();
        }
        return Runtime.getRuntime().maxMemory();
    }

    public long systemMemoryFree() {
        if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            return sunBean.getFreeMemorySize();
        }
        return Runtime.getRuntime().freeMemory();
    }

    public String hostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
