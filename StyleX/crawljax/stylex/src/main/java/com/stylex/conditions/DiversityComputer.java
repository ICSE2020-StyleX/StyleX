package com.stylex.conditions;

import com.stylex.chrome.CoverageInfo;
import com.stylex.distance.APTEDDistance;
import com.stylex.distance.LevenshteinDistance;
import com.stylex.jscoverage.StateVertexWithCoverage;
import com.stylex.util.Util;
import com.crawljax.util.DomUtils;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiversityComputer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiversityComputer.class);
    private static final APTEDDistance APTED_DISTANCE = new APTEDDistance();
    private static final LevenshteinDistance LEVENSHTEIN_DISTANCE = new LevenshteinDistance();
    private static final Map<String, Float> COMPUTED_DISTANCES_LEVENSHTEIN = new HashMap<>();
    private static final Map<String, Float> COMPUTED_DISTANCES_APTED = new HashMap<>();

    public static void main(String[] args) {

        //String eventsStatesPath = args[0];
        File resultsFolder = new File(args[0]);

        doEventsDiversity(resultsFolder);
        //doFinalTimeDiversity(resultsFolder);
        //doInstantDiversity(resultsFolder);
    }

    private static void doInstantDiversity(File resultsFolder) {
        File[] subjectFolders = resultsFolder.listFiles((dir, name) -> dir.isDirectory());
        for (File subjectFolder : subjectFolders) {
            List<File> coverageFiles = Util.searchForFiles(subjectFolder.getAbsolutePath(), "coverage", true);
            for (File coverageFile : coverageFiles) {
                File currentResultsFolder = coverageFile.getParentFile();
                String technique = currentResultsFolder.getName();
                File outFile = new File(currentResultsFolder.getAbsolutePath() + File.separator + "computed-diversity.txt");
                try {
                    Util.writeStringToFile("Distance" + System.lineSeparator(), outFile, false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                LOGGER.info("Now doing {} for {}", technique, subjectFolder.getName());
                List<File> allDiscoveredStatesJSONs = Util.searchForFiles(coverageFile.getAbsolutePath(), ".json", false);
                List<StateVertexWithCoverage> allDiscoveredStates = readStatesFromJSONs(allDiscoveredStatesJSONs);
                allDiscoveredStates.sort(Comparator.comparingLong(StateVertexWithCoverage::getExplorationTimeNanos));
                for (int i = 1; i < allDiscoveredStates.size(); i++) {
                    float instantDiversity = computeInstantDiversityAPTED(allDiscoveredStates.get(i), allDiscoveredStates.get(i - 1));
                    try {
                        Util.writeStringToFile(String.format("%s", instantDiversity) + System.lineSeparator(), outFile, true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static void doFinalTimeDiversity(File resultsFolder) {

        File[] subjectFolders = resultsFolder.listFiles((dir, name) -> dir.isDirectory());
        for (File subjectFolder : subjectFolders) {
            File outFile = new File(subjectFolder.getAbsolutePath() + File.separator + "final-computed-diversity.txt");
            try {
                Util.writeStringToFile("Universe_APTED\tUniverse_Levenshtein\tAvgDOMSize\tTechnique" + System.lineSeparator(), outFile, false);
            } catch (IOException e) {
                e.printStackTrace();
            }
            List<File> coverageFiles = Util.searchForFiles(subjectFolder.getAbsolutePath(), "coverage", true);
            for (File coverageFile : coverageFiles) {
                File currentResultsFolder = coverageFile.getParentFile();
                String technique = currentResultsFolder.getName();
                LOGGER.info("Now doing {} for {}", technique, subjectFolder.getName());
                List<File> allDiscoveredStatesJSONs = Util.searchForFiles(coverageFile.getAbsolutePath(), ".json", false);
                List<StateVertexWithCoverage> allDiscoveredStates = readStatesFromJSONs(allDiscoveredStatesJSONs);
                float universeDiversityApted = computeUniverseDiversityAPTED(allDiscoveredStates, allDiscoveredStates.size() - 1);
                float universeDiversityLevenshtein = computeUniverseDiversityLeveneshtein(allDiscoveredStates, allDiscoveredStates.size() - 1);;
                float averageDOMSize = getAverageDOMSize(allDiscoveredStates);
                try {
                    Util.writeStringToFile(String.format("%s\t%s\t%s\t%s", universeDiversityApted, universeDiversityLevenshtein, averageDOMSize, technique) + System.lineSeparator(), outFile, true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static float computeUniverseDiversityAPTED(List<StateVertexWithCoverage> statesFoundAfterEvents, int i) {
        return computeUniverseDiversity(statesFoundAfterEvents, i, DiversityComputer::computeInstantDiversityAPTED);
    }

    private static float computeUniverseDiversityLeveneshtein(List<StateVertexWithCoverage> allDiscoveredStates, int i) {
        return computeUniverseDiversity(allDiscoveredStates, i, DiversityComputer::computeInstantDiversityLeveneshtein);
    }

    private static float computeUniverseDiversity(List<StateVertexWithCoverage> states, int lastStateIndexInclusive,
                                                  BiFunction<StateVertexWithCoverage, StateVertexWithCoverage, Float> f) {
        List<StateVertexWithCoverage> currentStatesToConsider = states.subList(0, lastStateIndexInclusive + 1);
        Map<String, StateVertexWithCoverage> currentStates = new HashMap<>();
        for (StateVertexWithCoverage stateVertexWithCoverage : currentStatesToConsider) {
            if (!currentStates.containsKey(stateVertexWithCoverage.getName())) {
                currentStates.put(stateVertexWithCoverage.getName(), stateVertexWithCoverage);
            }
        }
        currentStatesToConsider = new ArrayList<>(currentStates.values());
        float sum = 0;
        for (int i = 0; i < currentStatesToConsider.size(); i++) {
            for (int j = i + 1; j < currentStatesToConsider.size(); j++) {
                StateVertexWithCoverage s1 = currentStatesToConsider.get(i);
                StateVertexWithCoverage s2 = currentStatesToConsider.get(j);
                float diversity = f.apply(s1, s2);
                sum += diversity;
            }
        }
        return sum / currentStatesToConsider.size(); // average
    }

    private static float computeInstantDiversityLeveneshtein(StateVertexWithCoverage s1, StateVertexWithCoverage s2) {
        LOGGER.info("Computing Levenshtein distance between {} and {}", s1.getName(), s2.getName());
        if (COMPUTED_DISTANCES_LEVENSHTEIN.containsKey(getKey(s1, s2))) {
            return COMPUTED_DISTANCES_LEVENSHTEIN.get(getKey(s1, s2));
        } else if (COMPUTED_DISTANCES_LEVENSHTEIN.containsKey(getKey(s2, s1))) {
            return COMPUTED_DISTANCES_LEVENSHTEIN.get(getKey(s2, s1));
        } else {
            try {
                Document doc1 = DomUtils.asDocument(s1.getStrippedDom());
                Document doc2 = DomUtils.asDocument(s2.getStrippedDom());
                float rawDistance = LEVENSHTEIN_DISTANCE.getDOMDistance(doc1, doc2);
                float distance = rawDistance / Math.max(s1.getStrippedDom().length(), s2.getStrippedDom().length());
                COMPUTED_DISTANCES_LEVENSHTEIN.put(getKey(s1, s2), distance);
                COMPUTED_DISTANCES_LEVENSHTEIN.put(getKey(s2, s1), distance);
                return distance;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    private static float getAverageDOMSize(List<StateVertexWithCoverage> allDiscoveredStates) {
        int sum = 0;
        for (StateVertexWithCoverage discoveredState : allDiscoveredStates) {
            try {
                Document doc = DomUtils.asDocument(discoveredState.getStrippedDom());
                int numberOfNodes = Util.getNumberOfNodes(doc);
                sum += numberOfNodes;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return (float) sum / allDiscoveredStates.size();
    }

    private static void doEventsDiversity(File resultsFolder) {
        File[] subjectFolders = resultsFolder.listFiles((dir, name) -> dir.isDirectory());
        for (File subjectFolder : subjectFolders) {
            List<File> coverageFiles = Util.searchForFiles(subjectFolder.getAbsolutePath(), "coverage", true);
            File outFile = new File(subjectFolder.getAbsolutePath() + File.separator + "number-of-hashes.txt");
            try {
                Util.writeStringToFile("Event\tNumberOfHashes\tTotalSize\tTechnique" + System.lineSeparator(), outFile, false);
            } catch (IOException e) {
                e.printStackTrace();
            }
            coverageFiles.sort(Comparator.comparing(File::getName));
            for (File coverageFile : coverageFiles) {
                File currentResultsFolder = coverageFile.getParentFile();
                String technique = currentResultsFolder.getName();
                LOGGER.info("Now doing {} for {}", technique, outFile.getParentFile().getParentFile());

                // Get events times
                List<Integer> eventTimes = new ArrayList<>();
                File coverageInfoFilePath = new File(currentResultsFolder.getAbsolutePath() + File.separator + "events-coverage.txt");
                try {
                    String contensts = Util.readFileToString(coverageInfoFilePath);
                    Pattern p = Pattern.compile("Event #(\\d+) at (\\d+) seconds");
                    Matcher matcher = p.matcher(contensts);
                    while (matcher.find()) {
                        int seconds = Integer.valueOf(matcher.group(2));
                        eventTimes.add(seconds);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                List<StateVertexWithCoverage> statesFoundAfterEvents = new ArrayList<>();

                List<File> allDiscoveredStatesJSONs = Util.searchForFiles(coverageFile.getAbsolutePath(), ".json", false);
                List<StateVertexWithCoverage> allDiscoveredStates = readStatesFromJSONs(allDiscoveredStatesJSONs);
                allDiscoveredStates.sort((s1, s2) -> Float.compare(s1.getExplorationTimeNanos(), s2.getExplorationTimeNanos()));

                statesFoundAfterEvents.add(allDiscoveredStates.get(0));
                int currentlyAddedStateIndex = 0;
                for (int timeIndex = 1; timeIndex < eventTimes.size(); timeIndex++) {
                    StateVertexWithCoverage currentlyAddedState = allDiscoveredStates.get(currentlyAddedStateIndex);
                    StateVertexWithCoverage nextStateToAdd = currentlyAddedState;
                    if (currentlyAddedStateIndex < allDiscoveredStates.size() - 1) {
                        nextStateToAdd = allDiscoveredStates.get(currentlyAddedStateIndex + 1);
                    }
                    int nextStateToAddTime = (int) Math.floor(nextStateToAdd.getExplorationTimeNanos() / 1e9);
                    if (eventTimes.get(timeIndex) > nextStateToAddTime) {
                        statesFoundAfterEvents.add(nextStateToAdd);
                        if (currentlyAddedStateIndex < allDiscoveredStates.size() - 1) {
                            currentlyAddedStateIndex++;
                        }
                    } else {
                        statesFoundAfterEvents.add(currentlyAddedState);
                    }
                }

                for (int i = 0; i < statesFoundAfterEvents.size(); i++) {
                    StateVertexWithCoverage stateVertexWithCoverage = statesFoundAfterEvents.get(i);
                    int numberOfHashes = stateVertexWithCoverage.getAllIDsForScriptsCovered().size();
                    int size = 0;
                    Map<String, CoverageInfo> currentCoverageInfo = stateVertexWithCoverage.getCurrentCoverageInfo();
                    for (String id : currentCoverageInfo.keySet()) {
                        size += currentCoverageInfo.get(id).getScriptLength();
                    }
                    try {
                        Util.writeStringToFile(String.format("%s\t%s\t%s\t%s", i + 1, numberOfHashes, size, technique) + System.lineSeparator(), outFile, true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static float computeInstantDiversityAPTED(StateVertexWithCoverage s1, StateVertexWithCoverage s2) {
        LOGGER.info("Computing distance between {} and {}", s1.getName(), s2.getName());
        if (COMPUTED_DISTANCES_APTED.containsKey(getKey(s1, s2))) {
            return COMPUTED_DISTANCES_APTED.get(getKey(s1, s2));
        } else if (COMPUTED_DISTANCES_APTED.containsKey(getKey(s2, s1))) {
            return COMPUTED_DISTANCES_APTED.get(getKey(s2, s1));
        } else {
            try {
                Document doc1 = DomUtils.asDocument(s1.getStrippedDom());
                Document doc2 = DomUtils.asDocument(s2.getStrippedDom());
                float rawDistance = APTED_DISTANCE.getDOMDistance(doc1, doc2);
                float distance = rawDistance / Math.max(Util.getNumberOfNodes(doc1), Util.getNumberOfNodes(doc2));
                COMPUTED_DISTANCES_APTED.put(getKey(s1, s2), distance);
                COMPUTED_DISTANCES_APTED.put(getKey(s2, s1), distance);
                return distance;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    private static String getKey(StateVertexWithCoverage s1, StateVertexWithCoverage s2) {
        return s1.getName() + "," + s2.getName();
    }

    private static List<StateVertexWithCoverage> readStatesFromJSONs(List<File> stateFiles) {
        Gson gson = new Gson();
        List<StateVertexWithCoverage> stateVertexWithCoverageList = new ArrayList<>();
        for (File json : stateFiles) {
            try {
                String jsonContents = Util.readFileToString(json);
                StateVertexWithCoverage stateVertexWithCoverage = gson.fromJson(jsonContents, StateVertexWithCoverage.class);
                stateVertexWithCoverageList.add(stateVertexWithCoverage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (stateVertexWithCoverageList.size() != stateFiles.size()) {
            throw new RuntimeException("An error occurred while reading the files");
        }
        return stateVertexWithCoverageList;
    }

}
