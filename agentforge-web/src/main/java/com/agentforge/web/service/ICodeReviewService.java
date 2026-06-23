package com.agentforge.web.service;

import java.util.List;
import java.util.Map;

/**
 * 代码审查编排服务接口。
 */
public interface ICodeReviewService {

    Map<String, Object> reviewAndStore(String repoUrl, Integer prNumber,
                                       String commitId, String diff);

    Map<String, Object> reviewStoreAndComment(String repoUrl, Integer prNumber,
                                              String commitId, String diff, boolean postComment);

    Map<String, Object> getById(Long id);

    void reviewAsync(String repoUrl, Integer prNumber, String commitId, String diff);

    void reviewAsyncWithFetch(String repoUrl, Integer prNumber, String commitId, String patchUrl);

    List<Map<String, Object>> listRecent(int limit);
}
