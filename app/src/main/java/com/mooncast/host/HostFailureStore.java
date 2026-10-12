package com.mooncast.host;

import java.io.*;
import java.nio.file.*;

/** Bounded, private, process-independent startup result; never stores PINs or identity keys. */
final class HostFailureStore {
    record Failure(long token, String stage, String error) {}
    private static final int MAGIC = 0x4d434831;
    private static File target(File directory) { return new File(directory,"host-startup-result"); }

    static synchronized void save(File directory, long token, String stage, String error) {
        if (token <= 0) return;
        Path temporary = new File(directory,"host-startup-result.tmp").toPath();
        try {
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temporary))) {
                out.writeInt(MAGIC); out.writeLong(token);
                out.writeUTF(bound(stage,32)); out.writeUTF(bound(error,1024));
            }
            Files.move(temporary,target(directory).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ignored) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignoredCleanup) {}
        }
    }

    static Failure read(File directory, long expectedToken) {
        if (expectedToken <= 0) return null;
        try {
            Path file=target(directory).toPath();
            if(Files.size(file)>4096)return null;
            try(DataInputStream in=new DataInputStream(Files.newInputStream(file))) {
                if(in.readInt()!=MAGIC)return null;
                long token=in.readLong();String stage=in.readUTF(),error=in.readUTF();
                if(token!=expectedToken || stage.length()>32 || error.length()>1024 || in.read()!=-1)return null;
                return new Failure(token,stage,error);
            }
        } catch(IOException | RuntimeException ignored) { return null; }
    }

    static void failure(File directory, long token, String stage, String error, Runnable publish, Runnable stop) {
        save(directory,token,stage,error);
        try { publish.run(); } catch(RuntimeException ignored) {}
        finally { stop.run(); }
    }
    private static String bound(String value,int limit) {
        return value==null?"":value.substring(0,Math.min(value.length(),limit));
    }
}
