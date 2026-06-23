package com.agentforge.common.model;

import lombok.Data;

@Data
public class IntentResult {
      private String intent;       // REPORT / ORDER / ANALYSIS / CODE
      private Double confidence;   // 0.0 ~ 1.0
      private String reasoning;    // 分类依据
}