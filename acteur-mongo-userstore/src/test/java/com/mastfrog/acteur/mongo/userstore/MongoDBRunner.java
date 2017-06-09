/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
