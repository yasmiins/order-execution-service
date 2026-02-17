package io.github.yasmiins.orderexecutionservice.web;

import java.util.Map;

public record ApiError(String message, Map<String, String> details) {
}
