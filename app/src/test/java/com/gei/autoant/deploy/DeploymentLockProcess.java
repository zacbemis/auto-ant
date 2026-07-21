package com.gei.autoant.deploy;

import java.nio.file.Path;

public final class DeploymentLockProcess {
    private DeploymentLockProcess() { }

    public static void main(String[] args) throws Exception {
        ReconcileConfiguration configuration = ReconcileConfiguration.load(Path.of(args[0]));
        try (DeploymentLock ignored = DeploymentLock.acquire(configuration, 0)) {
            System.out.println("LOCKED");
            System.out.flush();
            Thread.sleep(Long.parseLong(args[1]));
        }
    }
}
