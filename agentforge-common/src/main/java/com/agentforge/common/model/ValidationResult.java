 package com.agentforge.common.model;

  import lombok.AllArgsConstructor;
  import lombok.Builder;
  import lombok.Data;
  import lombok.NoArgsConstructor;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public class ValidationResult {
      private boolean passed;
      private String errorMessage;
      private String riskLevel; // SAFE / WARNING / DANGEROUS

      public static ValidationResult safe() {
          return ValidationResult.builder()
                  .passed(true)
                  .riskLevel("SAFE")
                  .build();
      }

      public static ValidationResult fail(String message) {
          return ValidationResult.builder()
                  .passed(false)
                  .errorMessage(message)
                  .riskLevel("DANGEROUS")
                  .build();
      }

      public static ValidationResult warn(String message) {
          return ValidationResult.builder()
                  .passed(true)
                  .errorMessage(message)
                  .riskLevel("WARNING")
                  .build();
      }
  }