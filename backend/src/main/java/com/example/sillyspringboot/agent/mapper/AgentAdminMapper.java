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

    int updateTaskStatus(@Param("id") long id,
                         @Param("fromStatuses") List<String> fromStatuses,
                         @Param("toStatus") String toStatus,
                         @Param("clearError") boolean clearError,
                         @Param("retryIncrement") int retryIncrement,
                         @Param("terminal") boolean terminal);

    int insertEvent(Map<String, Object> event);

    Map<String, Object> findLatestPlan(@Param("taskId") long taskId);

    int insertPlan(Map<String, Object> plan);

    int insertStep(Map<String, Object> step);

    int updateTaskPlanSummary(@Param("taskId") long taskId,
                              @Param("totalSteps") int totalSteps,
                              @Param("currentStepId") long currentStepId);

    int updateTaskRiskLevel(@Param("taskId") long taskId,
                            @Param("riskLevel") String riskLevel);

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

    int failExhaustedSteps(@Param("now") LocalDateTime now);

    int failTasksWithFailedSteps();

    int completeStep(@Param("stepId") long stepId,
                     @Param("owner") String owner,
                     @Param("outputJson") String outputJson,
                     @Param("outputSummary") String outputSummary);

    int failStep(@Param("stepId") long stepId,
                 @Param("owner") String owner,
                 @Param("errorCode") String errorCode,
                 @Param("errorMessage") String errorMessage);

    Map<String, Object> findNextBlockedStep(@Param("taskId") long taskId);

    int promoteStep(@Param("stepId") long stepId);

    int updateTaskProgress(@Param("taskId") long taskId);

    int tryCompleteTask(@Param("taskId") long taskId);

    int tryCompletePartialTask(@Param("taskId") long taskId);

    int failTask(@Param("taskId") long taskId,
                 @Param("errorCode") String errorCode,
                 @Param("errorMessage") String errorMessage);

    int refreshTaskUsage(@Param("taskId") long taskId);

    int taskBudgetExceeded(@Param("taskId") long taskId);

    int taskRuntimeExceeded(@Param("taskId") long taskId);

    int cancelPendingSteps(@Param("taskId") long taskId);

    int finalizeCancelledTask(@Param("taskId") long taskId);

    int resetFailedSteps(@Param("taskId") long taskId);

    Map<String, Object> findToolByKey(@Param("toolKey") String toolKey);

    int requestToolApproval(@Param("stepId") long stepId);

    int approveToolStep(@Param("stepId") long stepId);

    int rejectToolStep(@Param("stepId") long stepId,
                       @Param("errorCode") String errorCode,
                       @Param("errorMessage") String errorMessage);

    int insertToolCall(Map<String, Object> toolCall);

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
                    @Param("keyword") String keyword);

    List<Map<String, Object>> listTasks(@Param("status") String status,
                                        @Param("riskLevel") String riskLevel,
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

    List<Map<String, Object>> listEvents(@Param("taskId") long taskId,
                                         @Param("afterSeq") long afterSeq,
                                         @Param("limit") int limit);

    List<Map<String, Object>> listArtifacts(@Param("taskId") long taskId);

    long countApprovals(@Param("status") String status,
                        @Param("keyword") String keyword);

    List<Map<String, Object>> listApprovals(@Param("status") String status,
                                            @Param("keyword") String keyword,
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
}
