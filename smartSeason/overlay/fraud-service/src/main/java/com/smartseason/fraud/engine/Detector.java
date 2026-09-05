package com.smartseason.fraud.engine;

import java.util.List;

public interface Detector {

    Typology typology();

    String ruleCode();

    List<Detection> evaluate(WorkerActivity activity, RuleSettings settings);
}
