package com.mooncast.host;

import java.nio.file.*;
import java.util.*;

public final class HostFailureStoreTest {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("mooncast-startup-result-");
        try{
            HostFailureStore.save(dir.toFile(),71,"identity","");
            require(HostFailureStore.read(dir.toFile(),72)==null,"Previous launch result cannot leak to a new launch");
            List<String> order=new ArrayList<>();
            HostFailureStore.failure(dir.toFile(),71,"identity","NoClassDefFoundError: fixture",()->{
                require(HostFailureStore.read(dir.toFile(),71).error().contains("NoClassDefFoundError"),"Error survives before publishing");
                order.add("publish");
            },()->order.add("stop"));
            require(order.equals(List.of("publish","stop")),"Publish precedes fast cleanup");
            HostFailureStore.failure(dir.toFile(),71,"identity","retained",()->{throw new RuntimeException("dead callback");},()->order.add("stopped despite callback"));
            require(order.get(2).equals("stopped despite callback"),"Failed delivery still cleans up");
            require(HostFailureStore.read(dir.toFile(),71).error().equals("retained"),"Failed delivery retains exact correlated error");
            HostFailureStore.save(dir.toFile(),72,"native-ready","");
            require(HostFailureStore.read(dir.toFile(),72).error().isEmpty(),"New startup does not inherit old failure");
            HostFailureStore.save(dir.toFile(),72,"x".repeat(90),"z".repeat(4000));
            var bounded=HostFailureStore.read(dir.toFile(),72);
            require(bounded.stage().length()==32&&bounded.error().length()==1024,"Persisted diagnostics are bounded");
            Files.write(dir.resolve("host-startup-result"),new byte[5000]);
            require(HostFailureStore.read(dir.toFile(),72)==null,"Oversized result rejected");
            Files.write(dir.resolve("host-startup-result"),new byte[]{0,1});
            require(HostFailureStore.read(dir.toFile(),72)==null,"Corruption cannot crash UI");
            System.out.println("PASS startup diagnostics: generation isolation, persist-before-publish-before-stop, failed delivery, bounds and corruption");
        }finally{
            try(var paths=Files.list(dir)){for(Path p:paths.toList())Files.deleteIfExists(p);}Files.deleteIfExists(dir);
        }
    }
}
