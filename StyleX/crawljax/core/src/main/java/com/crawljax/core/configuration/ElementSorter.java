package com.crawljax.core.configuration;

import com.crawljax.core.CandidateElement;

import java.util.List;

public interface ElementSorter {

    List<CandidateElement> sort(List<CandidateElement> results);

}
