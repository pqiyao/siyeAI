package com.example.sillyspringboot.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Mapper
public interface AgentAdminMapper {

    int insertTask(Map<String, Object> task);

    Map<String, Object> findTaskByKey(@Param("taskKey") String taskKey);

    int updateTaskStatus(@Param("id") long id,
                         @Param("fromStatuses") List<String> fromStatuses,
                         @Param("toStatus") String toStatus,
                         @Param("clearError") boolean clearError,
                         @Param("retryIncrement") int retryIncrement,
                         @Param("terminal") boolean terminal);

    int insertEvent(Map<String, Object> event);

    int insertCollaboration(Map<String, Object> message);

    List<Map<String, Object>> listCollaborations(@Param("taskId") long taskId);

    Map<String, Object> findLatestPlan(@Param("taskId") long taskId);

    int insertPlan(Map<String, Object> plan);

    int insertStep(Map<String, Object> step);

    int deleteRuntimeReplanSteps(@Param("taskId") long taskId,
                                 @Param("stepIds") List<Long> stepIds);

    int updateTaskPlanSummary(@Param("taskId") long taskId,
                              @Param("totalSteps") int totalSteps,
                              @Param("currentStepId") long currentStepId);

    int updateTaskRiskLevel(@Param("taskId") long taskId,
                            @Param("riskLevel") String riskLevel);

    int updateTaskExecutionLimits(@Param("taskId") long taskId,
                                  @Param("maxSteps") int maxSteps,
                                  @Param("maxRuntimeSeconds") int maxRuntimeSeconds,
                                  @Param("maxTokens") long maxTokens,
                                  @Param("maxCost") BigDecimal maxCost);

    int updatePlanStatus(@Param("id") long id,
                         @Param("fromStatus") String fromStatus,
                         @Param("toStatus") String toStatus,
                         @Param("approvedBy") Long approvedBy);

    int insertApproval(Map<String, Object> approval);

    Map<String, Object> findLatestApproval(@Param("taskId") long taskId,
                                           @Param("approvalType") String approvalType);

    Map<String, Object> findApproval(@Param("id") long id);

    int updateApprovalStatus(@Param("id") long id,
                             @Param("fromStatus") String fromStatus,
                             @Param("toStatus") String toStatus,
                             @Param("reviewerId") Long reviewerId,
                             @Param("decisionComment") String decisionComment);

    int deletePlanSteps(@Param("planId") long planId);

    int resetTaskPlanSummary(@Param("taskId") long taskId);

    List<Map<String, Object>> listReadySteps(@Param("limit") int limit);

    int claimStep(@Param("stepId") long stepId,
                  @Param("owner") String owner,
                  @Param("leaseUntil") LocalDateTime leaseUntil);

    int renewStepLease(@Param("stepId") long stepId,
                       @Param("owner") String owner,
                       @Param("leaseUntil") LocalDateTime leaseUntil);

    int recoverExpiredSteps(@Param("now") LocalDateTime now);

    List<Map<String, Object>> listRecoverableExpiredSteps(@Param("now") LocalDateTime now);

    int failExhaustedSteps(@Param("now") LocalDateTime now);

    int failTasksWithFailedSteps();

    int cancelStepsForFailedTasks();

    int completeStep(@Param("stepId") long stepId,
                     @Param("owner") String owner,
                     @Param("outputJson") String outputJson,
                     @Param("outputSummary") String outputSummary);

    int failStep(@Param("stepId") long stepId,
                 @Param("owner") String owner,
                 @Param("errorCode") String errorCode,
                 @Param("errorMessage") String errorMessage);

    int retryStep(@Param("stepId") long stepId,
                  @Param("owner") String owner,
                  @Param("errorCode") String errorCode,
                  @Param("errorMessage") String errorMessage);

    int skipStep(@Param("stepId") long stepId,
                 @Param("owner") String owner,
                 @Param("errorCode") String errorCode,
                 @Param("errorMessage") String errorMessage);

    List<Map<String, Object>> findReadyBlockedSteps(@Param("taskId") long taskId);

    int promoteStep(@Param("stepId") long stepId);

    int updateTaskProgress(@Param("taskId") long taskId);

    int incrementTaskTotalSteps(@Param("taskId") long taskId,
                                @Param("delta") int delta);

    int tryCompleteTask(@Param("taskId") long taskId);

    int tryCompletePartialTask(@Param("taskId") long taskId);

    int failTask(@Param("taskId") long taskId,
                 @Param("errorCode") String errorCode,
                 @Param("errorMessage") String errorMessage);

    int refreshTaskUsage(@Param("taskId") long taskId);

    int taskBudgetExceeded(@Param("taskId") long taskId);

    int tenantDailyBudgetExceeded(@Param("tenantKey") String tenantKey);

    int platformBudgetExceeded(@Param("maxTokens") long maxTokens,
                               @Param("maxCost") java.math.BigDecimal maxCost);

    Map<String, Object> dailyBudgetUsage();

    int taskRuntimeExceeded(@Param("taskId") long taskId);

    int cancelPendingSteps(@Param("taskId") long taskId);

    int finalizeCancelledTask(@Param("taskId") long taskId);

    int resetFailedSteps(@Param("taskId") long taskId);

    int resetDegradedSteps(@Param("taskId") long taskId);

    Map<String, Object> findToolByKey(@Param("toolKey") String toolKey);

    int requestToolApproval(@Param("stepId") long stepId);

    int approveToolStep(@Param("stepId") long stepId);

    int rejectToolStep(@Param("stepId") long stepId,
                       @Param("errorCode") String errorCode,
                       @Param("errorMessage") String errorMessage);

    int failWaitingApprovalStep(@Param("stepId") long stepId,
                                @Param("errorCode") String errorCode,
                                @Param("errorMessage") String errorMessage);

    int rejectPendingToolApproval(@Param("stepId") long stepId,
                                  @Param("errorCode") String errorCode,
                                  @Param("errorMessage") String errorMessage);

    int failExpiredApprovalSteps();

    int failExpiredApprovalTasks();

    int rejectExpiredPlans();

    int expirePendingApprovals();

    int insertToolCall(Map<String, Object> toolCall);

    int insertUsageLedger(Map<String, Object> usage);

    long countUsageLedger(@Param("tenantKey") String tenantKey, @Param("taskId") Long taskId);

    List<Map<String, Object>> listUsageLedger(@Param("tenantKey") String tenantKey,
                                              @Param("taskId") Long taskId,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    Map<String, Object> tenantUsageSummary(@Param("tenantKey") String tenantKey);

    Map<String, Object> findTenantBudget(@Param("tenantKey") String tenantKey);

    List<Map<String, Object>> listTenantBudgetAlerts();


    Map<String, Object> tenantDailyBudgetUsage(@Param("tenantKey") String tenantKey);

    int upsertTenantBudget(Map<String, Object> budget);

    int resetTenantDailyTaskReservation(@Param("tenantKey") String tenantKey);

    int reserveTenantDailyTask(@Param("tenantKey") String tenantKey);

    int finishToolCall(@Param("id") long id,
                       @Param("status") String status,
                       @Param("outputSummary") String outputSummary,
                       @Param("errorCode") String errorCode,
                       @Param("errorMessage") String errorMessage,
                       @Param("durationMs") long durationMs,
                       @Param("tokenUsage") long tokenUsage,
                       @Param("costAmount") BigDecimal costAmount,
                       @Param("provider") String provider,
                       @Param("modelName") String modelName);

    int upsertArtifact(Map<String, Object> artifact);

    Map<String, Object> findArtifact(@Param("taskId") long taskId,
                                     @Param("artifactKey") String artifactKey,
                                     @Param("version") int version);

    int refreshArtifactCount(@Param("taskId") long taskId);

    Map<String, Object> overview();

    long countTasks(@Param("status") String status,
                    @Param("riskLevel") String riskLevel,
                    @Param("tenantKey") String tenantKey,
                    @Param("keyword") String keyword);

    List<Map<String, Object>> listTasks(@Param("status") String status,
                                        @Param("riskLevel") String riskLevel,
                                        @Param("tenantKey") String tenantKey,
                                        @Param("keyword") String keyword,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    long countTasksForOwner(@Param("ownerId") long ownerId,
                            @Param("status") String status,
                            @Param("keyword") String keyword);

    List<Map<String, Object>> listTasksForOwner(@Param("ownerId") long ownerId,
                                                @Param("status") String status,
                                                @Param("keyword") String keyword,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    Map<String, Object> findTask(@Param("id") long id);

    List<Map<String, Object>> listSteps(@Param("taskId") long taskId);

    List<Map<String, Object>> listToolCalls(@Param("taskId") long taskId);

    Map<String, Object> summarizeToolUsage(@Param("taskId") long taskId);

    List<Map<String, Object>> listEvents(@Param("taskId") long taskId,
                                         @Param("afterSeq") long afterSeq,
                                         @Param("limit") int limit);

    List<Map<String, Object>> listArtifacts(@Param("taskId") long taskId);

    long countArtifactCenter(@Param("artifactType") String artifactType,
                             @Param("status") String status,
                             @Param("keyword") String keyword,
                             @Param("tenantKey") String tenantKey);

    List<Map<String, Object>> listArtifactCenter(@Param("artifactType") String artifactType,
                                                 @Param("status") String status,
                                                 @Param("keyword") String keyword,
                                                 @Param("tenantKey") String tenantKey,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    long countApprovals(@Param("status") String status,
                        @Param("keyword") String keyword,
                        @Param("tenantKey") String tenantKey);

    List<Map<String, Object>> listApprovals(@Param("status") String status,
                                            @Param("keyword") String keyword,
                                            @Param("tenantKey") String tenantKey,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    long countTools(@Param("category") String category,
                    @Param("enabled") Boolean enabled,
                    @Param("keyword") String keyword);

    List<Map<String, Object>> listTools(@Param("category") String category,
                                        @Param("enabled") Boolean enabled,
                                        @Param("keyword") String keyword,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    Map<String, Object> findTool(@Param("id") long id);

    long countAgentRoles(@Param("enabled") Boolean enabled,
                         @Param("keyword") String keyword);

    List<Map<String, Object>> listAgentRoles(@Param("enabled") Boolean enabled,
                                             @Param("keyword") String keyword,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    Map<String, Object> findAgentRole(@Param("id") long id);

    Map<String, Object> findAgentRoleForExecution(@Param("roleKey") String roleKey,
                                                  @Param("version") int version);

    int nextAgentRoleVersion(@Param("roleKey") String roleKey);

    int insertAgentRoleDefinition(Map<String, Object> role);

    int disableAgentRoleVersions(@Param("roleKey") String roleKey);

    int enableAgentRoleVersion(@Param("id") long id);

    Map<String, Object> findAgentRoleForMutation(@Param("id") long id);

    long countWorkflowTemplates(@Param("enabled") Boolean enabled,
                                @Param("taskType") String taskType,
                                @Param("keyword") String keyword);

    List<Map<String, Object>> listWorkflowTemplates(@Param("enabled") Boolean enabled,
                                                    @Param("taskType") String taskType,
                                                    @Param("keyword") String keyword,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    Map<String, Object> findWorkflowTemplate(@Param("id") long id);

    Map<String, Object> findEnabledWorkflowTemplateForExecution(@Param("taskType") String taskType,
                                                                @Param("workflowKey") String workflowKey);

    int nextWorkflowTemplateVersion(@Param("workflowKey") String workflowKey);

    int insertWorkflowTemplate(Map<String, Object> workflow);

    int disableWorkflowTemplateVersions(@Param("workflowKey") String workflowKey);

    int enableWorkflowTemplateVersion(@Param("id") long id);

    Map<String, Object> findWorkflowTemplateForMutation(@Param("id") long id);

    int insertConfigAudit(Map<String, Object> audit);

    long countConfigAudits(@Param("configType") String configType,
                           @Param("configKey") String configKey,
                           @Param("action") String action,
                           @Param("operatorId") Long operatorId,
                           @Param("keyword") String keyword);

    List<Map<String, Object>> listConfigAudits(@Param("configType") String configType,
                                               @Param("configKey") String configKey,
                                               @Param("action") String action,
                                               @Param("operatorId") Long operatorId,
                                               @Param("keyword") String keyword,
                                               @Param("offset") int offset,
                                               @Param("limit") int limit);

    Map<String, Object> budgetSummary(@Param("taskType") String taskType,
                                      @Param("status") String status,
                                      @Param("tenantKey") String tenantKey);

    long countBudgetTasks(@Param("taskType") String taskType,
                          @Param("status") String status,
                          @Param("overBudget") Boolean overBudget,
                          @Param("tenantKey") String tenantKey);

    List<Map<String, Object>> listBudgetTasks(@Param("taskType") String taskType,
                                              @Param("status") String status,
                                              @Param("overBudget") Boolean overBudget,
                                              @Param("tenantKey") String tenantKey,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    Map<String, Object> evaluationSummary(@Param("taskType") String taskType,
                                           @Param("verdict") String verdict,
                                           @Param("minimumScore") Integer minimumScore,
                                           @Param("gatePassed") Boolean gatePassed,
                                           @Param("tenantKey") String tenantKey);

    long countEvaluationArtifacts(@Param("taskType") String taskType,
                                  @Param("verdict") String verdict,
                                  @Param("minimumScore") Integer minimumScore,
                                  @Param("gatePassed") Boolean gatePassed,
                                  @Param("tenantKey") String tenantKey);

    List<Map<String, Object>> listEvaluationArtifacts(@Param("taskType") String taskType,
                                                       @Param("verdict") String verdict,
                                                       @Param("minimumScore") Integer minimumScore,
                                                       @Param("gatePassed") Boolean gatePassed,
                                                       @Param("tenantKey") String tenantKey,
                                                       @Param("offset") int offset,
                                                       @Param("limit") int limit);

    long countTenants(@Param("status") String status, @Param("keyword") String keyword);

    List<Map<String, Object>> listTenants(@Param("status") String status,
                                          @Param("keyword") String keyword,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    Map<String, Object> findTenant(@Param("tenantKey") String tenantKey);

    int ensureTenant(@Param("tenantKey") String tenantKey,
                     @Param("displayName") String displayName,
                     @Param("createdBy") Long createdBy);

    int insertTenant(Map<String, Object> tenant);

    int updateTenantStatus(@Param("tenantKey") String tenantKey,
                           @Param("status") String status,
                           @Param("updatedBy") Long updatedBy);

    long countTenantMembers(@Param("tenantKey") String tenantKey, @Param("status") String status);

    List<Map<String, Object>> listTenantMembers(@Param("tenantKey") String tenantKey,
                                                @Param("status") String status,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    Map<String, Object> findTenantMember(@Param("tenantKey") String tenantKey, @Param("userId") long userId);

    int insertTenantMember(Map<String, Object> member);

    int updateTenantMemberStatus(@Param("tenantKey") String tenantKey,
                                 @Param("userId") long userId,
                                 @Param("status") String status,
                                 @Param("memberRole") String memberRole,
                                 @Param("updatedBy") Long updatedBy);

    List<Map<String, Object>> listAdminTenantScopes(@Param("adminId") long adminId,
                                                    @Param("status") String status);

    Map<String, Object> findAdminTenantScope(@Param("adminId") long adminId,
                                             @Param("tenantKey") String tenantKey);

    int upsertAdminTenantScope(Map<String, Object> scope);

    int updateAdminTenantScopeStatus(@Param("adminId") long adminId,
                                     @Param("tenantKey") String tenantKey,
                                     @Param("status") String status,
                                     @Param("updatedBy") Long updatedBy);
}
