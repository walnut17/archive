package com.archive.common;

/**
 * find_project 5 级隐式项目切换判定 (RI-23).
 */
public enum SwitchDecision {
    /** 同项目 + 高置信, 自动锁定. */
    SAME_CONFIRMED,
    /** 同项目 + 中置信, hint 注入. */
    SAME_PROBABLY,
    /** 不同项目 + 中高置信, 反问切换. */
    DIFFERENT_PROBABLY,
    /** 低置信, 反问用户. */
    UNCLEAR
}
