package com.stylex.util;

import com.crawljax.browser.EmbeddedBrowser;
import org.w3c.dom.*;

import java.awt.geom.Rectangle2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class Util {

    public static String runOSCommand(String[] command) throws Exception {
        Runtime runtime = Runtime.getRuntime();
        try {
            Process process = runtime.exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            process.waitFor();  // wait for process to complete

            return builder.toString();
        } catch (IOException | InterruptedException ex) {
            throw new Exception(ex);
        }
    }

    public static List<String> readFileLines(String filePath) {
        List<String> fileLines = new ArrayList<>();
        try  {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)));
            String line;
            while ((line = reader.readLine()) != null) {
                fileLines.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileLines;
    }

    public static List<File> searchForFiles(String path, final String suffix, boolean recursive) {

        List<File> toReturn = new ArrayList<>();

        File currentDirectoryFile = new File(path);
        File[] matchingFiles = currentDirectoryFile.listFiles((directory, name) -> name.endsWith(suffix));
        if (null != matchingFiles) {
            toReturn.addAll(Arrays.asList(matchingFiles));
        }

        if (recursive) {
            File[] directories = currentDirectoryFile.listFiles((current, name) -> new File(current, name).isDirectory());
            if (null != directories) {
                for (File directory : directories) {
                    toReturn.addAll(searchForFiles(directory.getAbsolutePath(), suffix, true));
                }
            }
        }

        return toReturn;

    }

    public static String readFileToString(File jsonFile) throws IOException {
        return new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
    }

    public static void writeStringToFile(String string, File file, boolean append) throws IOException {
        BufferedWriter fw = new BufferedWriter(new FileWriter(file, append));
        fw.append(string);
        fw.close();
    }

    public static Rectangle2D.Double getElementBoundingBox(EmbeddedBrowser browser, String xPath) {
        /*
         * It is necessary to get the bounding box from JS.
         * The WebElement#getRect method has not been implemented in the
         * web drivers that I used:
         * webDriver.findElement(By.xpath(PathHelper.getXPathExpression(node))).getRect(); // WON'T WORK
         * The scrolling should be taken into account:
         * https://developer.mozilla.org/en-US/docs/Web/API/Element/getBoundingClientRect
         */
        Object executedJavaScript;
        String javascript = "return window.scrollX";
        executedJavaScript = browser.executeJavaScript(javascript);
        double windowScrollX = getDoubleValue(executedJavaScript);
        javascript = "return window.scrollY";
        executedJavaScript = browser.executeJavaScript(javascript);
        double windowScrollY = getDoubleValue(executedJavaScript);
        javascript = "return function(){var e=document.evaluate(\"%s\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;if(e){return e.getBoundingClientRect();} else {console.log(\"%s\");return null;}}()";
        javascript = String.format(javascript, xPath, xPath);
        executedJavaScript = browser.executeJavaScript(javascript);
        if (executedJavaScript instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) executedJavaScript;
            double x = getDoubleValue(map.get("left")) + windowScrollX;
            double y = getDoubleValue(map.get("top")) + windowScrollY;
            double width = getDoubleValue(map.get("width"));
            double height = getDoubleValue(map.get("height"));
            return new Rectangle2D.Double(x, y, width, height);
        }
        return null;
    }

    public static double getDoubleValue(Object object) {
        if (object instanceof Number) {
            Number n = (Number) object;
            return n.doubleValue();
        }
        return 0;
    }

    public static int getNumberOfDescendants(Element element) {
        return element.getElementsByTagName("*").getLength();
    }

    private static final String DEPTH_CACHE = "DEPTH_CACHE";
    public static int getDepth(Node node) {
        Object depthCache = node.getUserData(DEPTH_CACHE);
        if (null != depthCache) {
            return (int) depthCache;
        } else {
            int depth = -1;
            Node parentNode = node.getParentNode();
            if (parentNode != null) {
                Object parentDepth = parentNode.getUserData(DEPTH_CACHE);
                if (null != parentDepth) {
                    depth = (int) parentDepth + 1;
                }
            }
            if (depth == -1) {
                Node currentNode = node;
                while (currentNode != null) {
                    depth++;
                    currentNode = currentNode.getParentNode();
                }
            }
            node.setUserData(DEPTH_CACHE, depth, null);
            return depth;
        }
    }

    public static int getNodeSubtreeHeight(Node node) {
        int subtreeHeight = -1;
        NodeList childNodes = node.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            subtreeHeight = Math.max(subtreeHeight, getNodeSubtreeHeight(child));
        }
        return subtreeHeight + 1;
    }

    public static int getNumberOfNodes(Document document) {
        int numberOfNodes = 0;
        LinkedList<Node> nodeList = new LinkedList<>();
        nodeList.add(document);
        while (!nodeList.isEmpty()) {
            Node node = nodeList.removeFirst();
            numberOfNodes++;
            NodeList childNodes = node.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                nodeList.add(childNodes.item(i));
            }
        }
        return numberOfNodes;
    }

    public static List<Element> bfs(Element element) {
        List<Element> elements = new ArrayList<>();
        Queue<Element> currentNodes = new LinkedList<>();
        currentNodes.add(element);
        while (!currentNodes.isEmpty()) {
            Element currentNode = currentNodes.remove();
            elements.add(currentNode);
            NodeList childNodes = currentNode.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node item = childNodes.item(i);
                if (item instanceof Element) {
                    currentNodes.add((Element) item);
                }
            }
        }
        return elements;
    }

    public static Map<String, String> getAttributeValue(Element element) {
        Map<String, String> attributeValueMap = new HashMap<>();
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            attributeValueMap.put(attr.getNodeName().toLowerCase().trim(), attr.getNodeValue());
        }
        return attributeValueMap;
    }

    public static boolean isClickableByDefault(Element element) {
        switch (element.getTagName().toUpperCase()) {
            case "A":
            case "BUTTON":
                return true;
            case "INPUT":
                String type = element.getAttribute("type");
                if (type != null) {
                    if ("BUTTON".equalsIgnoreCase(type) ||
                            "SUBMIT".equalsIgnoreCase(type) ||
                            "IMAGE".equalsIgnoreCase(type)) {
                        return true;
                    }
                }
                break;
            default:
        }
        return false;
    }
}
