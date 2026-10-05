package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class TagTaxonomyIntegrationTest {

    /** 7 个一级 + 32 个二级，与 V91 的条数一致。 */
    private static final int TAXONOMY_SIZE = 39;

    /** 开发库：种子先插了 6 个固定 id 的节点，V91 只补缺的，不重复、不改 id。 */
    @Nested
    @SpringBootTest(properties =
            "spring.datasource.url=jdbc:h2:mem:taxonomy-seeded;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                    + "NON_KEYWORDS=USER,YEAR,VALUE;DB_CLOSE_DELAY=-1")
    @AutoConfigureMockMvc
    class WithDevSeed {

        @Autowired
        private JdbcTemplate jdbc;

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        void seededTagsKeepTheirIdsAndNothingIsDuplicated() {
            assertEquals(TAXONOMY_SIZE, jdbc.queryForObject("SELECT COUNT(*) FROM tag", Integer.class));
            assertEquals(3L, jdbc.queryForObject("SELECT id FROM tag WHERE slug = 'divisibility'", Long.class));
            assertEquals(2L, jdbc.queryForObject("SELECT parent_id FROM tag WHERE slug = 'divisibility'", Long.class));
        }

        /** 种子第 3 题只挂了二级的「数的整除」，按一级「数论」筛也要能筛到。 */
        @Test
        void filteringByParentIncludesChildren() throws Exception {
            String body = mockMvc.perform(get("/api/v1/problems").param("tagId", "2"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Long> ids = new ArrayList<>();
            for (JsonNode item : objectMapper.readTree(body).get("items")) {
                ids.add(item.get("id").asLong());
            }
            assertTrue(ids.contains(3L), "数论下应包含挂在「数的整除」上的第 3 题，实际：" + ids);
        }
    }

    /** 正式库不挂种子：知识点树完全由 V91 建出来。 */
    @Nested
    @SpringBootTest(properties = {
            "spring.datasource.url=jdbc:h2:mem:taxonomy-prod;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                    + "NON_KEYWORDS=USER,YEAR,VALUE;DB_CLOSE_DELAY=-1",
            "spring.flyway.locations=classpath:db/migration/h2",
            "mathematics.dev.seed-demo-user=false"})
    class WithoutSeed {

        @Autowired
        private JdbcTemplate jdbc;

        @Test
        void wholeTreeIsCreatedWithTwoLevels() {
            assertEquals(TAXONOMY_SIZE, jdbc.queryForObject("SELECT COUNT(*) FROM tag", Integer.class));
            assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM tag WHERE parent_id IS NULL", Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM tag c JOIN tag p ON p.id = c.parent_id WHERE p.parent_id IS NOT NULL",
                    Integer.class), "只允许两级");
        }
    }
}
