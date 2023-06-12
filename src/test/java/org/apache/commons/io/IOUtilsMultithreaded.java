/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.io;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IOUtilsMultithreaded {
    long seed = 1;

    @BeforeEach
    public void setUp() throws Exception {
        seed = new Random().nextLong();
    }

    @Test
    public void testSkipFullyOnInflaterInputStream() throws Exception {
        long thisSeed = seed;
        //thisSeed = -727624427837034313l;
        Random random = new Random(thisSeed);
        byte[] bytes = IOUtils.toByteArray(this.getClass().getResourceAsStream("TIKA-4065.bin"));
        int numSkips = (random.nextInt(bytes.length) / 100) + 1;

        int skips[] = generateSkips(bytes, numSkips, random);
        int[] expected =
                generateExpected(inflate(bytes), skips);

        int numThreads = 2;
        int iterations = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<Integer> executorCompletionService =
                new ExecutorCompletionService<>(executorService);

        for (int i = 0; i < numThreads; i++) {
            executorCompletionService.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    for (int iteration = 0; iteration < iterations; iteration++) {
                        try (InputStream is =
                                     new InflaterInputStream(new ByteArrayInputStream(bytes), new Inflater(true))) {
                            for (int skipIndex = 0; skipIndex < skips.length; skipIndex++) {
                                try {
                                    IOUtils.skipFully(is, skips[skipIndex]);
                                    int c = is.read();
                                    assertEquals(expected[skipIndex], c,
                                            "failed on seed=" + seed + " iteration=" + iteration);
                                } catch (EOFException e) {
                                    assertEquals(expected[skipIndex], is.read(), "failed on " +
                                            "seed=" + seed + " iteration=" + iteration);
                                }
                            }
                        }
                    }
                    return 1;
                }
            });
        }

        int finished = 0;
        while (finished < numThreads) {
            //blocking
            Future<Integer> future = executorCompletionService.take();
            try {
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
                fail("failed on seed=" + seed);
            }
            finished++;
        }
    }

    private InputStream inflate(byte[] deflated) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(new InflaterInputStream(new ByteArrayInputStream(deflated),
                new Inflater(true)), bos);
        return new ByteArrayInputStream(bos.toByteArray());
    }


    private int[] generateExpected(InputStream is, int[] skips) throws IOException {
        int[] testBytes = new int[skips.length];
        for (int i = 0; i < skips.length; i++) {
            try {
                IOUtils.skipFully(is, skips[i]);
                testBytes[i] = is.read();
            } catch (EOFException e) {
                testBytes[i] = -1;
            }
        }
        return testBytes;
    }

    private int[] generateSkips(byte[] bytes, int numSkips, Random random) {
        int[] skips = new int[numSkips];
        for (int i = 0; i < skips.length; i++) {
            skips[i] = random.nextInt(bytes.length / numSkips) + bytes.length/10;
        }
        return skips;
    }

}