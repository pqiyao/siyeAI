package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.mapper.AdminVisitorRiskMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminVisitorRiskControllerTest {

    @Test
    void hardDeletesSecurityEventsBeforeVisitorDevices() {
        AdminVisitorRiskMapper mapper = mock(AdminVisitorRiskMapper.class);
        AdminVisitorRiskController controller = new AdminVisitorRiskController(mapper);
        List<Long> ids = List.of(11L, 12L);
        when(mapper.countDevicesByIds(ids)).thenReturn(2L);
        when(mapper.deleteEventsByDeviceIds(ids)).thenReturn(5);
        when(mapper.deleteDevicesByIds(ids)).thenReturn(2);

        Map<String, Object> result = controller.hardDelete(
                new AdminVisitorRiskController.DeleteRequest(ids)
        );

        assertThat(result.get("code")).isEqualTo(200);
        assertThat(result.get("deleted")).isEqualTo(2);
        assertThat(result.get("eventsDeleted")).isEqualTo(5);
        var order = inOrder(mapper);
        order.verify(mapper).deleteEventsByDeviceIds(ids);
        order.verify(mapper).deleteDevicesByIds(ids);
    }

    @Test
    void refusesPartialOrStaleDeviceSelection() {
        AdminVisitorRiskMapper mapper = mock(AdminVisitorRiskMapper.class);
        AdminVisitorRiskController controller = new AdminVisitorRiskController(mapper);
        List<Long> ids = List.of(21L, 22L);
        when(mapper.countDevicesByIds(ids)).thenReturn(1L);

        Map<String, Object> result = controller.hardDelete(
                new AdminVisitorRiskController.DeleteRequest(ids)
        );

        assertThat(result.get("code")).isEqualTo(500);
        assertThat(result.get("msg")).asString().contains("不存在");
        verify(mapper, never()).deleteEventsByDeviceIds(anyList());
        verify(mapper, never()).deleteDevicesByIds(anyList());
    }
}
