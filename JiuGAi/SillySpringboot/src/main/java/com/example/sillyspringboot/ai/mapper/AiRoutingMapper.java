package com.example.sillyspringboot.ai.mapper;

import com.example.sillyspringboot.ai.entity.AiProviderAccount;
import com.example.sillyspringboot.ai.entity.AiProviderDeployment;
import com.example.sillyspringboot.ai.entity.AiResolvedDeployment;
import com.example.sillyspringboot.ai.entity.AiRoute;
import com.example.sillyspringboot.ai.entity.AiRouteMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiRoutingMapper {
    List<AiProviderAccount> listAccounts();
    AiProviderAccount findAccountById(@Param("id") Long id);
    AiProviderAccount findAccountByKey(@Param("providerKey") String providerKey);
    int insertAccount(AiProviderAccount row);
    int updateAccount(AiProviderAccount row);
    int deleteAccount(@Param("id") Long id);
    int countDeploymentsForAccount(@Param("accountId") Long accountId);

    List<AiProviderDeployment> listDeployments();
    List<AiProviderDeployment> listDeploymentsByAccountId(@Param("accountId") Long accountId);
    AiProviderDeployment findDeploymentById(@Param("id") Long id);
    AiProviderDeployment findDeploymentByIdForUpdate(@Param("id") Long id);
    int insertDeployment(AiProviderDeployment row);
    int updateDeployment(AiProviderDeployment row);
    int countDuplicateDeployment(
            @Param("accountId") Long accountId,
            @Param("capability") String capability,
            @Param("modelName") String modelName,
            @Param("voiceName") String voiceName,
            @Param("excludeId") Long excludeId
    );
    int deleteDeployment(@Param("id") Long id);
    int countRouteMembersForDeployment(@Param("deploymentId") Long deploymentId);
    int markDeploymentSuccess(@Param("id") Long id);
    int resetDeploymentHealth(@Param("id") Long id);
    int resetDeploymentHealthByAccountId(@Param("accountId") Long accountId);
    int markDeploymentStatus(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("lastError") String lastError
    );
    int markDeploymentFailure(
            @Param("id") Long id,
            @Param("consecutiveFailures") int consecutiveFailures,
            @Param("circuitOpenUntil") LocalDateTime circuitOpenUntil,
            @Param("lastError") String lastError
    );
    int tryAcquireHalfOpenProbe(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil
    );

    List<AiRoute> listRoutes();
    AiRoute findRouteById(@Param("id") Long id);
    AiRoute findRouteByKey(@Param("routeKey") String routeKey);
    int insertRoute(AiRoute row);
    int updateRoute(AiRoute row);
    int deleteRoute(@Param("id") Long id);
    List<AiRouteMember> listRouteMembers(@Param("routeId") Long routeId);
    int deleteRouteMembers(@Param("routeId") Long routeId);
    int insertRouteMember(AiRouteMember row);
    List<AiResolvedDeployment> resolveRoute(@Param("routeKey") String routeKey);
}
