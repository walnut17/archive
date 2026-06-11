package com.archive.agent.tool.archive;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ArchiveFs 动作注册表.
 * 所有 @Component ArchiveFsAction 实现通过构造注入自动注册.
 */
@Component
public class ArchiveFsActionRegistry {

    private final Map<String, ArchiveFsAction> actionMap;

    public ArchiveFsActionRegistry(List<ArchiveFsAction> actions) {
        this.actionMap = actions.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ArchiveFsAction::actionName,
                        a -> a,
                        (a, b) -> { throw new IllegalStateException("重复 action: " + a.actionName()); }
                ));
    }

    public ArchiveFsAction getAction(String name) {
        ArchiveFsAction action = actionMap.get(name);
        if (action == null) {
            throw new IllegalArgumentException("未知 action: " + name + "，支持: " + actionMap.keySet());
        }
        return action;
    }

    public List<String> supportedActions() {
        return List.copyOf(actionMap.keySet());
    }
}
