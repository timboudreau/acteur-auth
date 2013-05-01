/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mastfrog.acteur.mongo.userstore;

import com.google.inject.Inject;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.settings.MutableSettings;
import java.io.File;
import java.io.IOException;

/**
 *
 * @author tim
 */
public class MongoDBRunner implements Runnable {

    private Process mongo;
    private File mongoDir;
    private final int port;

    @Inject
    MongoDBRunner(ShutdownHookRegistry reg, MutableSettings settings) throws IOException, InterruptedException {
        this.port = 29001;
        settings.setInt("mongoPort", port);
        settings.setString("mongoHost", "localhost");
        reg.add(this);
        mongoDir = createMongoDir();
        mongo = startMongoDB();
    }

    public boolean wasAbleToRun() {
        return mongo != null;
    }

    private File createMongoDir() {
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        File mongoDir = new File(tmp, "mongo-" + System.currentTimeMillis());
        if (!mongoDir.mkdirs()) {
            throw new AssertionError("Could not create " + mongoDir);
        }
        System.out.println("MONGO DIR " + mongoDir);
        return mongoDir;
    }

    private Process startMongoDB() throws IOException, InterruptedException {
        if (mongoDir == null) {
            mongoDir = createMongoDir();
        }
        ProcessBuilder pb = new ProcessBuilder().command("mongod", "--dbpath",
                mongoDir.getAbsolutePath(), "--nojournal", "--smallfiles", "-nssize", "1",
                "--noprealloc", "--slowms", "5", "--port", "" + port);

        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        Process result = pb.start();
        Thread.sleep(1000);
        try {
            int code = result.exitValue();
            System.out.println("MongoDB process exited with " + code);
            return null;
        } catch (IllegalThreadStateException ex) {
            return result;
        }
    }

    @Override
    public void run() {
        System.out.println("Shutdown");
        try {
            if (mongo != null) {
                mongo.destroy();
            }
        } finally {
            if (mongoDir.exists()) {
                cleanup(mongoDir);
            }
        }
    }

    private void cleanup(File dir) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                cleanup(f);
                f.delete();
            }
        }
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                f.delete();
            }
        }
        dir.delete();
    }
}
