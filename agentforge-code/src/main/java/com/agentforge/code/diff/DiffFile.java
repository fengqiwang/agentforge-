package com.agentforge.code.diff;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 变更文件模型（单个文件的 diff 解析结果）。
 */
@Data
public class DiffFile {

    /** 文件路径，如 src/main/java/com/agentforge/Foo.java */
    private String path;

    /** 文件分类：JAVA / SQL / XML / CONFIG / OTHER */
    private String category;

    /** 变更类型：ADDED / MODIFIED / DELETED / RENAMED */
    private String changeType;

    /** 新增的代码行 */
    private List<String> addedLines = new ArrayList<>();

    /** 删除的代码行 */
    private List<String> deletedLines = new ArrayList<>();

    /** 变更前后的代码行（含 + / - / 上下文，供 Review 用） */
    private List<String> rawDiff = new ArrayList<>();

    /** 估算的变更行数。 */
    public int changedLineCount() {
        return addedLines.size() + deletedLines.size();
    }

    public enum Category {
        JAVA, SQL, XML, CONFIG, OTHER
    }
}
