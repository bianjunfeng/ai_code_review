package com.aipr.review.service.github;

import com.aipr.review.common.BusinessException;
import com.aipr.review.enums.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PrUrlParser {

    private static final Pattern GITHUB_PR_URL = Pattern.compile(
            "^https://github\\.com/([^/]+)/([^/]+)/pull/(\\d+)(?:[/?#].*)?$"
    );

    public ParsedPrUrl parse(String prUrl) {
        if (prUrl == null) {
            throw new BusinessException(ErrorCode.PR_URL_FORMAT_ERROR);
        }

        Matcher matcher = GITHUB_PR_URL.matcher(prUrl.trim());
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.PR_URL_FORMAT_ERROR);
        }

        return new ParsedPrUrl(
                matcher.group(1),
                matcher.group(2),
                Integer.parseInt(matcher.group(3))
        );
    }
}
