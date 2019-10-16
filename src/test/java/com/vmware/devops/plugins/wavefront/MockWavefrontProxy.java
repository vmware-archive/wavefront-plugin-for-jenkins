/*
 * Copyright (c) 2019 VMware, Inc. All Rights Reserved.
 *
 * SPDX-License-Identifier: MIT
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.vmware.devops.plugins.wavefront;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MockWavefrontProxy {
    private static final Logger LOGGER = Logger.getLogger(MockWavefrontProxy.class.getName());
    private ServerSocket serverSocket;
    private boolean terminated = false;
    private Thread getMessagesThread;

    List<String> result = new ArrayList<>();

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        Runnable getMessages = () -> {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(5100);
                result = new ArrayList<>();
                BufferedReader inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                long lastMessageReceived = System.currentTimeMillis();
                while (!isTerminated() || System.currentTimeMillis() - lastMessageReceived < 5000) {
                    try {
                        String line = inputStream.readLine();
                        lastMessageReceived = System.currentTimeMillis();
                        result.add(line);
                    } catch (IOException e) {
                        LOGGER.log(Level.FINE, "Socket timeout is reached", e);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        getMessagesThread = new Thread(getMessages);
        getMessagesThread.start();
    }

    public boolean isTerminated() {
        return terminated;
    }

    public List<String> terminate() throws IOException, InterruptedException {
        terminated = true;
        serverSocket.close();
        getMessagesThread.join();
        return result;
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    public static Entry<MockWavefrontProxy, Integer> initMockedWavefrontProxy(int minPortNumber, int maxPortNumber) {
        int port;
        MockWavefrontProxy proxy;
        while (true) {
            try {
                Random r = new Random();
                port = new Integer(r.nextInt(maxPortNumber - minPortNumber) + minPortNumber);
                proxy = new MockWavefrontProxy();
                proxy.start(port);
                if (proxy.getServerSocket() != null) {
                    Entry<MockWavefrontProxy, Integer> mockedProxy = new SimpleEntry<>(proxy, port);
                    return mockedProxy;
                }
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Port is occupied, try another", e);
            }
        }
    }
}
