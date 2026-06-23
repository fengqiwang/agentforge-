package com.agentforge.common.model;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserContext {
      private Long userId;
      private String username;
      private String role; // ADMIN / CITY_MANAGER / BRANCH_STAFF

      public static UserContext fromRequest(HttpServletRequest request) {
          return (UserContext) request.getAttribute("currentUser");
      }
}