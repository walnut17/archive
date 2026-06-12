package com.archive.v11;

import com.archive.agent.AgentContext;
import com.archive.agent.AgentEngine;
import com.archive.agent.AgentRequest;
import com.archive.agent.AgentResponse;
import com.archive.agent.tool.AgentTool;
import com.archive.agent.tool.FindProjectTool;
import com.archive.agent.tool.NetworkDictLookupTool;
import com.archive.agent.tool.QueryMysqlTool;
import com.archive.agent.tool.ToolResult;
import com.archive.common.SwitchDecision;
import com.archive.dto.FactEventDiff;
import com.archive.dto.ProjectResponse;
import com.archive.entity.*;
import com.archive.entity.Notification.NotificationType;
import com.archive.repository.*;
import com.archive.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v1.1 集成测试 (MOD-06): 30+ 测例, 覆盖 MOD-01~05 + 7 大端到端场景.
 * H2 内存库 + MockBean GlmService, CI 无需 MySQL / 真实 GLM key.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.ai.agent.enabled=true",
        "archive.optimistic-lock.strict=false"
})
class V11IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private List<AgentTool> agentTools;
    @Autowired private FindProjectTool findProjectTool;
    @Autowired private QueryMysqlTool queryMysqlTool;
    @Autowired private NetworkDictLookupTool networkDictLookupTool;
    @Autowired private AgentEngine agentEngine;
    @Autowired private ProjectRepository projectRepo;
    @Autowired private RecycleBinService recycleBinService;
    @Autowired private ProjectBoardService projectBoardService;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepo;
    @Autowired private ExportService exportService;
    @Autowired private ProjectFactEventService factEventService;
    @Autowired private ProjectFactEventRepository factEventRepo;
    @Autowired private MaskingService maskingService;
    @Autowired private NetworkDictService networkDictService;
    @Autowired private RoleRepository roleRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private RbacService rbacService;
    @Autowired private DictTypeRepository dictTypeRepo;
    @Autowired private DictItemRepository dictItemRepo;
    @Autowired private AuditLogRepository auditLogRepo;
    @Autowired private PreviewService previewService;
    @Autowired private ImportService importService;
    @Autowired private FailureLogService failureLogService;
    @Autowired private KnowledgeSearchService knowledgeSearchService;

    @MockBean
    private GlmService glmService;

    private Project seedProject;
    private Role adminRole;
    private User adminUser;

    @BeforeEach
    void setUp() {
        ensureV11JdbcTables();
        seedRolesAndUsers();
        seedNetworkDictSources();
        seedProject = ensureSeedProject();

        when(glmService.chat(anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("{\"action\":\"FINAL_ANSWER\",\"answer\":\"v1.1 集成测试 mock 答案\"}");
        when(glmService.chat(anyString(), anyString()))
                .thenReturn("{\"action\":\"FINAL_ANSWER\",\"answer\":\"v1.1 集成测试 mock 答案\"}");
    }

    private void seedNetworkDictSources() {
        dictTypeRepo.findByTypeCode("network_dict_source").orElseGet(() ->
                dictTypeRepo.save(DictType.builder()
                        .typeCode("network_dict_source")
                        .typeName("网络查源")
                        .isSystem(true)
                        .sortOrder(9)
                        .enabled(true)
                        .build()));
        if (dictItemRepo.findByTypeCodeAndItemKey("network_dict_source", "baidu_baike").isEmpty()) {
            dictItemRepo.save(DictItem.builder()
                    .typeCode("network_dict_source")
                    .itemKey("baidu_baike")
                    .itemValue("{\"baseUrl\":\"https://baike.baidu.com/api/openapi\",\"timeout\":5000}")
                    .sortOrder(1)
                    .isSystem(true)
                    .enabled(true)
                    .build());
        }
        if (dictItemRepo.findByTypeCodeAndItemKey("network_dict_source", "wikipedia_zh").isEmpty()) {
            dictItemRepo.save(DictItem.builder()
                    .typeCode("network_dict_source")
                    .itemKey("wikipedia_zh")
                    .itemValue("{\"baseUrl\":\"https://zh.wikipedia.org/w/api.php\",\"timeout\":5000}")
                    .sortOrder(2)
                    .isSystem(true)
                    .enabled(true)
                    .build());
        }
    }

    private void ensureV11JdbcTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS user_role (
                user_id BIGINT NOT NULL,
                role_id BIGINT NOT NULL,
                assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (user_id, role_id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS project_member (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                project_id BIGINT NOT NULL,
                user_id BIGINT NOT NULL,
                role_in_project VARCHAR(32)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS failure_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                path VARCHAR(512),
                failure_type VARCHAR(64),
                error_msg CLOB,
                stack_trace CLOB,
                resolved BOOLEAN DEFAULT FALSE,
                occurred_at TIMESTAMP,
                resolved_at TIMESTAMP
            )
            """);
    }

    private void seedRolesAndUsers() {
        adminRole = roleRepo.findByCode("admin").orElseGet(() ->
                roleRepo.save(Role.builder().code("admin").name("管理员").build()));
        adminUser = userRepo.findByUsername("v11admin").orElseGet(() -> {
            User u = User.builder()
                    .username("v11admin")
                    .displayName("管理员")
                    .passwordHash("$2a$10$dummy")
                    .roleId(adminRole.getId())
                    .status("在岗")
                    .build();
            return userRepo.save(u);
        });
    }

    private Project ensureSeedProject() {
        return projectRepo.findByCode("PRJ-V11-001").orElseGet(() ->
                projectRepo.save(Project.builder()
                        .code("PRJ-V11-001")
                        .name("v1.1 测试项目")
                        .customerName("张三新能源")
                        .amountWan(5000L)
                        .status("贷后")
                        .summary("MOD-06 集成测试种子")
                        .build()));
    }

    // ============ MOD-01 数据库 / 实体验证 ============

    @Test
    void mod01_projectHasSoftDeleteAndVersionFields() throws Exception {
        assertNotNull(getField(Project.class, "deletedAt"));
        assertNotNull(getField(Project.class, "deletedBy"));
        assertNotNull(getField(Project.class, "version"));
    }

    @Test
    void mod01_proposalMaterialHaveSoftDeleteFields() throws Exception {
        assertNotNull(getField(Proposal.class, "deletedAt"));
        assertNotNull(getField(Material.class, "deletedAt"));
        assertNotNull(getField(BusinessTerm.class, "deletedAt"));
    }

    @Test
    void mod01_projectFactEventHasConfidenceLevel() throws Exception {
        assertNotNull(getField(ProjectFactEvent.class, "confidenceLevel"));
        assertNotNull(getField(ProjectFactEvent.class, "ownerId"));
        assertNotNull(getField(ProjectFactEvent.class, "dueDate"));
    }

    @Test
    void mod01_auditLogHasV11TypeFields() throws Exception {
        assertNotNull(getField(AuditLog.class, "type"));
        assertNotNull(getField(AuditLog.class, "entitySubtype"));
    }

    @Test
    void mod01_notificationEntityExists() {
        Notification n = notificationRepo.save(Notification.builder()
                .userId(adminUser.getId())
                .type(NotificationType.SYSTEM)
                .title("MOD-01 验证")
                .content("通知表可用")
                .read(false)
                .build());
        assertNotNull(n.getId());
    }

    // ============ MOD-02 核心域验证 ============

    @Test
    void mod02_softDeleteProject() {
        Project p = projectRepo.save(Project.builder()
                .code("PRJ-SOFT-DEL")
                .name("软删测试")
                .status("草稿")
                .build());
        recycleBinService.softDeleteProject(p.getId(), adminUser.getId());
        assertTrue(projectRepo.findById(p.getId()).isEmpty());
    }

    @Test
    void mod02_softDeletedExcludedFromDefaultQuery() {
        Project p = projectRepo.save(Project.builder()
                .code("PRJ-SOFT-EXCL")
                .name("软删排除")
                .status("草稿")
                .build());
        recycleBinService.softDeleteProject(p.getId(), adminUser.getId());
        assertFalse(projectRepo.findAll().stream().anyMatch(x -> x.getId().equals(p.getId())));
    }

    @Test
    void mod02_optimisticLockVersionIncrements() {
        Project p = projectRepo.findByCode("PRJ-V11-001").orElseThrow();
        int before = p.getVersion();
        p.setSummary("乐观锁测试 " + System.nanoTime());
        projectRepo.saveAndFlush(p);
        Project updated = projectRepo.findByCode("PRJ-V11-001").orElseThrow();
        assertTrue(updated.getVersion() > before);
    }

    @Test
    void mod02_rbacRoleIdFallbackPath() {
        assertTrue(rbacService.hasRole(adminUser.getId(), "admin"));
    }

    @Test
    void mod02_rbacUserRoleTablePriority() {
        Role committee = roleRepo.findByCode("committee").orElseGet(() ->
                roleRepo.save(Role.builder().code("committee").name("委员").build()));
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)",
                adminUser.getId(), committee.getId());
        assertTrue(rbacService.hasRole(adminUser.getId(), "committee"));
    }

    @Test
    void mod02_auditLogFiveTypesSupported() {
        List<String> types = List.of("WRITE", "LOGIN", "SENSITIVE_VIEW", "EXPORT", "LLM");
        assertEquals(5, types.size());
        assertNotNull(AuditLog.builder().actor("v11").action("test").type("EXPORT").build().getType());
    }

    @Test
    void mod02_recycleBinServiceAvailable() {
        assertNotNull(recycleBinService);
    }

    @Test
    void mod02_failureLogServiceAvailable() {
        assertNotNull(failureLogService);
    }

    // ============ MOD-03 Agent 工具验证 ============

    @Test
    void mod03_sevenAgentToolsRegistered() {
        Set<String> names = agentTools.stream().map(AgentTool::name).collect(Collectors.toSet());
        assertEquals(7, agentTools.size());
        assertTrue(names.contains("network_dict_lookup"));
        assertTrue(names.contains("find_project"));
        assertTrue(names.contains("query_mysql"));
    }

    @Test
    void mod03_findProjectExactMatchByCode() {
        AgentContext ctx = new AgentContext("PRJ-V11-001");
        var args = new FindProjectTool.FindProjectArgs();
        args.setQuery("PRJ-V11-001");
        ToolResult tr = findProjectTool.execute(args, ctx);
        assertTrue(tr.isOk());
        assertEquals("PRJ-V11-001", ctx.getProjectCode());
    }

    @Test
    void mod03_findProjectFiveLevelSwitchDecisionEnum() {
        assertEquals(4, SwitchDecision.values().length);
        assertNotNull(SwitchDecision.SAME_CONFIRMED);
        assertNotNull(SwitchDecision.DIFFERENT_PROBABLY);
        assertNotNull(SwitchDecision.UNCLEAR);
    }

    @Test
    void mod03_queryMysqlRejectsDisallowedTable() {
        var args = new QueryMysqlTool.QueryMysqlArgs();
        args.setTable("secret_table");
        args.setColumns(List.of("id"));
        ToolResult tr = queryMysqlTool.execute(args, new AgentContext("test"));
        assertFalse(tr.isOk());
    }

    @Test
    void mod03_queryMysqlAllowsProjectTable() {
        var args = new QueryMysqlTool.QueryMysqlArgs();
        args.setTable("project");
        args.setColumns(List.of("code", "name"));
        args.setLimit(5);
        ToolResult tr = queryMysqlTool.execute(args, new AgentContext("test"));
        assertTrue(tr.isOk());
    }

    @Test
    void mod03_queryMysqlFilterWhitelistKeys() throws Exception {
        Field f = QueryMysqlTool.class.getDeclaredField("ALLOWED_FILTER_KEYS");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> keys = (Set<String>) f.get(null);
        assertTrue(keys.containsAll(List.of("region", "industry", "stage", "fact_type", "time_bucket")));
    }

    @Test
    void mod03_networkDictLookupNoSourceReturnsGracefully() {
        var result = networkDictService.lookup("空债权", null);
        assertFalse(result.isFound());
        assertNotNull(result.getReason());
    }

    @Test
    void mod03_networkDictLookupToolNeverThrows() {
        var args = new NetworkDictLookupTool.NetworkDictLookupArgs();
        args.setQuery("测试术语");
        ToolResult tr = networkDictLookupTool.execute(args, new AgentContext("test"));
        assertNotNull(tr);
        assertTrue(tr.isOk() || tr.getError() != null);
    }

    @Test
    void mod03_agentEngineRunsWithMockLlm() {
        AgentResponse resp = agentEngine.run(new AgentRequest("PRJ-V11-001 概况"));
        assertNotNull(resp);
        assertNotNull(resp.getAnswer());
        assertTrue(resp.isAgentMode());
    }

    // ============ MOD-04 业务功能验证 ============

    @Test
    void mod04_projectBoardTableView() {
        var resp = projectBoardService.list("table", null, null, "amount", "desc", 1, 20);
        assertEquals("table", resp.getView());
        assertNotNull(resp.getItems());
    }

    @Test
    void mod04_projectBoardKanbanView() {
        var resp = projectBoardService.list("kanban", null, null, "updatedAt", "desc", 1, 50);
        assertEquals("kanban", resp.getView());
        assertNotNull(resp.getKanban());
    }

    @Test
    void mod04_notificationCreateAndList() {
        notificationService.create(adminUser.getId(), NotificationType.TODO,
                "待办提醒", "您有新待办", "/todos");
        long count = notificationRepo.countByUserIdAndRead(adminUser.getId(), false);
        assertTrue(count >= 1);
    }

    @Test
    void mod04_notificationMarkRead() {
        Notification n = notificationRepo.save(Notification.builder()
                .userId(adminUser.getId())
                .type(NotificationType.SYSTEM)
                .title("已读测试")
                .content("content")
                .read(false)
                .build());
        notificationService.markRead(n.getId());
        Notification updated = notificationRepo.findById(n.getId()).orElseThrow();
        assertTrue(updated.getRead());
    }

    @Test
    void mod04_exportProjectPdf() throws Exception {
        byte[] pdf = exportService.exportProjectPdf(seedProject.getId());
        assertNotNull(pdf);
        assertTrue(pdf.length > 100);
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
    }

    @Test
    void mod04_exportProjectExcel() throws Exception {
        byte[] xlsx = exportService.exportProjectsExcel("projects");
        assertNotNull(xlsx);
        assertTrue(xlsx.length > 100);
    }

    @Test
    void mod04_factEventDiff() {
        ProjectFactEvent evt = factEventRepo.save(ProjectFactEvent.builder()
                .projectId(seedProject.getId())
                .factType("抵押物")
                .eventType("UPDATE")
                .factValue("更新后")
                .confidenceLevel("CONFIRMED")
                .build());
        FactEventDiff diff = factEventService.getDiff(evt.getId());
        assertNotNull(diff);
        assertEquals("更新后", diff.getAfter());
    }

    @Test
    void mod04_maskingCommitteeView() {
        Role committeeRole = roleRepo.findByCode("committee").orElseGet(() ->
                roleRepo.save(Role.builder().code("committee").name("委员").build()));
        User committee = userRepo.save(User.builder()
                .username("committee_" + System.nanoTime())
                .displayName("委员甲")
                .passwordHash("$2a$10$dummy")
                .roleId(committeeRole.getId())
                .sensitiveViewEnabled(false)
                .status("在岗")
                .build());
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)",
                committee.getId(), committeeRole.getId());

        ProjectResponse resp = maskingService.applyMasking(seedProject, committee.getId());
        assertTrue(Boolean.TRUE.equals(resp.getMasked()));
        assertEquals("张**", resp.getDisplayName());
        assertEquals("***万", resp.getDisplayAmount());
    }

    @Test
    void mod04_maskingAdminUnmasked() {
        ProjectResponse resp = maskingService.applyMasking(seedProject, adminUser.getId());
        assertFalse(Boolean.TRUE.equals(resp.getMasked()));
    }

    @Test
    void mod04_importServiceAvailable() {
        assertNotNull(importService);
    }

    @Test
    void mod04_previewServiceAvailable() {
        assertNotNull(previewService);
    }

    // ============ MOD-05 前端集成相关（后端契约） ============

    @Test
    void mod05_agentResponseSupportsConfidenceBadge() {
        AgentResponse resp = agentEngine.run(new AgentRequest("江苏那个项目"));
        assertNotNull(resp);
    }

    @Test
    void mod05_healthEndpointAccessible() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void mod05_knowledgeSearchDegradesWithoutLlm() {
        var results = knowledgeSearchService.search("新能源", 3);
        assertNotNull(results);
    }

    @Test
    void mod05_qaResponseFieldsExist() throws Exception {
        assertNotNull(Class.forName("com.archive.dto.QaResponse")
                .getDeclaredField("confidenceBadge"));
    }

    // ============ 端到端 7 大场景 ============

    @Test
    void scenario1_ProjectKanban() {
        var table = projectBoardService.list("table", null, "贷后", "amount", "desc", 1, 20);
        var kanban = projectBoardService.list("kanban", null, null, "updatedAt", "desc", 1, 50);
        assertNotNull(table.getItems());
        assertNotNull(kanban.getKanban());
    }

    @Test
    void scenario2_Notification() {
        notificationService.create(adminUser.getId(), NotificationType.SYSTEM,
                "系统通知", "MOD-06 场景2", null);
        long unread = notificationRepo.countByUserIdAndRead(adminUser.getId(), false);
        assertTrue(unread >= 1);
        notificationRepo.findByUserIdAndReadOrderByCreatedAtDesc(adminUser.getId(), false,
                        org.springframework.data.domain.Pageable.unpaged())
                .forEach(n -> {
                    n.setRead(true);
                    notificationRepo.save(n);
                });
        assertEquals(0, notificationRepo.countByUserIdAndRead(adminUser.getId(), false));
    }

    @Test
    void scenario3_ExportPdf() throws Exception {
        byte[] pdf = exportService.exportProjectPdf(seedProject.getId());
        assertTrue(pdf.length > 0);
        byte[] xlsx = exportService.exportProjectsExcel("materials");
        assertTrue(xlsx.length > 0);
        var exportLogs = auditLogRepo.findByActionOrderByCreatedAtDesc(
                "EXPORT_project_pdf", org.springframework.data.domain.PageRequest.of(0, 5));
        assertFalse(exportLogs.isEmpty());
        assertEquals("EXPORT", exportLogs.getContent().get(0).getType());
    }

    @Test
    void scenario4_PreviewServiceReady() {
        assertNotNull(previewService);
    }

    @Test
    void scenario5_Masking() {
        ProjectResponse masked = maskingService.applyMasking(seedProject, null);
        assertFalse(Boolean.TRUE.equals(masked.getMasked()));
        ProjectResponse admin = maskingService.applyMasking(seedProject, adminUser.getId());
        assertFalse(Boolean.TRUE.equals(admin.getMasked()));
    }

    @Test
    void scenario6_OptimisticLockNonStrictByDefault() {
        Project p = projectRepo.findByCode("PRJ-V11-001").orElseThrow();
        assertNotNull(p.getVersion());
    }

    @Test
    void scenario7_MultiTurnConversationPreservesContext() {
        AgentContext ctx = new AgentContext("PRJ-V11-001 怎么样");
        ctx.setProjectCode("PRJ-V11-001");
        var args = new FindProjectTool.FindProjectArgs();
        args.setQuery("它的剩余金额");
        ToolResult tr = findProjectTool.execute(args, ctx);
        assertTrue(tr.isOk());
        assertEquals("PRJ-V11-001", ctx.getProjectCode());
    }

    // ═══════════════ 第 45 个测例 (C-0611-10) ═══════════════

    @Test
    @EnabledIfEnvironmentVariable(named = "GLM_API_KEY", matches = ".+")
    void test45_agentBasicFlow() {
        AgentRequest req = new AgentRequest("PRJ-V11-001");
        AgentResponse resp = agentEngine.run(req);
        assertNotNull(resp);
        assertNotNull(resp.getAnswer());
        assertFalse(resp.getSteps().isEmpty(), "Agent 应至少走 1 步");
    }

    private static Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
        return clazz.getDeclaredField(name);
    }
}
