package com.novelrealm.dto.library;

import java.util.List;

import com.novelrealm.dto.novel.NovelResponse;

/**
 * Une étagère avec le DÉTAIL de ses romans — renvoyée quand on consulte une
 * étagère précise.
 */
public record CategoryDetailResponse(
        Long id,
        String name,
        List<NovelResponse> novels
) {}
