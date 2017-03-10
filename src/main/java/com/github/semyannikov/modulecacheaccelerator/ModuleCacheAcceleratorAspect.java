package com.github.semyannikov.modulecacheaccelerator;

import com.documentum.fc.client.IDfSysObject;
import com.documentum.fc.common.IDfId;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Semyannikov Aleksandr
 */
@Aspect
public class ModuleCacheAcceleratorAspect {

    private Map<IDfId, String> downloadedObjectNames = new ConcurrentHashMap<IDfId, String>();
    private volatile boolean isMakingCacheConsistent = false;

    @Pointcut(value = "call(* *..InboundModuleMetadata.BofFile.persist(java.io.File)) " +
            "&& target(bofFile)")
    public void callPersistBofFilePointcut(Object bofFile) {}

    @Pointcut(value = "call(* *..ClassCacheManager.makeConsistent(..))")
    public void callMakeConsistentCachePointcut() {}

    @Around(value = "callPersistBofFilePointcut(bofFile)")
    public void aroundPersistBofFile(ProceedingJoinPoint joinPoint, Object bofFile) throws Throwable {
        if (isMakingCacheConsistent) {
            joinPoint.proceed();
            return;
        }
        Field sysObjectField = bofFile.getClass().getDeclaredField("m_sysObject");
        Field fileNameField = bofFile.getClass().getDeclaredField("m_savedAs");
        try {
            sysObjectField.setAccessible(true);
            fileNameField.setAccessible(true);
            IDfSysObject object = (IDfSysObject) sysObjectField.get(bofFile);
            String fileName = downloadedObjectNames.get(object.getObjectId());
            if (fileName == null) {
                joinPoint.proceed();
            }
            downloadedObjectNames.put(object.getObjectId(), (String) fileNameField.get(bofFile));
        } finally {
            sysObjectField.setAccessible(false);
            fileNameField.setAccessible(false);
        }
    }

    @Before(value = "callMakeConsistentCachePointcut()")
    public void beforeClassCacheManagerMakeConsistent() {
        isMakingCacheConsistent = true;
    }

    @After(value = "callMakeConsistentCachePointcut()")
    public void afterClassCacheManagerMakeConsistent() {
        isMakingCacheConsistent = false;
        downloadedObjectNames.clear();
    }
}
