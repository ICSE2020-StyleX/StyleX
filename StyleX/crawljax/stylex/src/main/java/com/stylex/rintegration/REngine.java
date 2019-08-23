package com.stylex.rintegration;

import org.rosuda.REngine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class REngine {

    // The following code was runnable on a simple project, now we have the libjri file in the root of crawljax (".")
    //static {
        //String path = new File(REngine.class.getClassLoader().getResource("libjri.jnilib").getPath()).getParent();
        //System.setProperty("java.library.path", System.getProperty("java.library.path") + File.pathSeparator + path);
    //}

    private static final Logger LOGGER = LoggerFactory.getLogger(REngine.class);

    public void loadEnvironmentDataFile(String modelEnvironmentFilePath) throws RCommandException {
        evaluate(String.format("load(\"%s\")", modelEnvironmentFilePath));
    }

    public void loadLibrary(String libraryName) throws RCommandException {
        evaluate(String.format("library(\"%s\")", libraryName));
    }

    public class RCommandException extends Throwable {
        public RCommandException(Exception e) {
            super(e);
        }

        public RCommandException(String message) {
            super(message);
        }
    }

    private org.rosuda.REngine.REngine rEngine;

    public void connect() {
        this.rEngine = connectREngine();
    }

    private org.rosuda.REngine.REngine connectREngine() {

        String rEngineClassName = "org.rosuda.REngine.JRI.JRIEngine";

        String[] args = {"--vanilla"}; // R workspace creation arguments: https://stackoverflow.com/a/32195021
        // No callbacks used, can use REngineStdOutput
        // (http://www.rforge.net/org/doc/org/rosuda/REngine/REngineStdOutput.html)
        REngineCallbacks callBacks = null;

        boolean runREPL = false; // Not using R in interactive (REPL) mode

        org.rosuda.REngine.REngine rengine = null;
        try {
            rengine = org.rosuda.REngine.REngine.getLastEngine();
            if (rengine == null) {
                rengine = org.rosuda.REngine.REngine.engineForClass(rEngineClassName, args, callBacks, runREPL);
                LOGGER.debug("Creating new REngine for {}", rEngineClassName);
            } else {
                LOGGER.debug("Got the last REngine");
            }
        } catch (Exception e) {
            LOGGER.warn("Error while getting the REngine instance");
            e.printStackTrace();
        }
        return rengine;

    }

    public void disconnect() {
        if (rEngine != null) {
            rEngine.close();
        }
    }

    public Object evaluate(String command) throws RCommandException {
        if (null != rEngine) {
            try {
                return getJavaObject(rEngine.parseAndEval(command));
            } catch (REngineException | REXPMismatchException e) {
                throw new RCommandException(e);
            }
        } else {
            throw new RCommandException("R Engine is not connected");
        }
    }

    private Object getJavaObject(REXP value) throws RCommandException {
        try {
            if (value.isFactor()) {
                RFactor rFactor = value.asFactor();
                if (rFactor.size() == 1) {
                    return rFactor.levelAtIndex(rFactor.indexAt(0));
                } else {
                    List<String> values = new ArrayList<>();
                    for (int i = 0; i < rFactor.size(); i++) {
                        values.add(rFactor.levelAtIndex(rFactor.indexAt(i)));
                    }
                    return values;
                }
            } else if (value.isString()) {
                return value.asString();
            } else if (value.isList()) {
                RList rList = value.asList();
                Map<String, Object> toReturn = new HashMap<>();
                for (Object listName : rList.keySet()) {
                    Object listValue = getJavaObject((REXP) rList.get(listName));
                    toReturn.put(listName.toString(), listValue);
                }
                return toReturn;

            } else if (value.isVector()) {
                return value.asIntegers();
            }
        } catch (REXPMismatchException e) {
            e.printStackTrace();
        }

        throw new RCommandException("This return type has not been implemented: " + value.toString());
    }
}
