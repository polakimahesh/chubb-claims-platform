package com.chubb.claims.controller;

import static com.chubb.claims.support.TestAuth.claimant;
import static com.chubb.claims.support.TestAuth.manager;
import static com.chubb.claims.support.TestAuth.officer1;
import static com.chubb.claims.support.TestAuth.officer2;
import static com.chubb.claims.support.TestAuth.otherClaimant;
import static com.chubb.claims.support.TestAuth.wrongPassword;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Authentication, authorisation, reassignment and document attachments. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"claims.outbox.enabled=false", "app.documents.dir=build/test-documents"})
class SecurityAndFeaturesIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String submit() throws Exception {
        String body = mvc.perform(post("/api/claims").with(claimant()).contentType(MediaType.APPLICATION_JSON)
                        .content(ClaimApiIntegrationTest.SUBMIT))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    private ResultActions act(RequestPostProcessor user, String url, String body) throws Exception {
        return mvc.perform(post(url).with(user).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void problem(ResultActions r, int status) throws Exception {
        r.andExpect(status().is(status)).andExpect(jsonPath("$.status").value(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------- authentication ----------

    @Test
    void noCredentialsIs401WithBasicChallenge() throws Exception {
        problem(mvc.perform(get("/api/claims")), 401);
        mvc.perform(get("/api/claims")).andExpect(header().string("WWW-Authenticate", "Basic realm=\"claims-platform\""));
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        problem(mvc.perform(get("/api/staff/claims/queue").with(wrongPassword())), 401);
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());   // exported contract must be public too
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    // ---------- role authorisation ----------

    @Test
    void rolesAreEnforced() throws Exception {
        String id = submit();
        problem(mvc.perform(get("/api/staff/claims/queue").with(claimant())), 403);           // claimant -> staff
        problem(act(claimant(), "/api/staff/claims/" + id + "/assign", ""), 403);
        problem(act(officer1(), "/api/claims", ClaimApiIntegrationTest.SUBMIT), 403);          // officer cannot submit
        problem(mvc.perform(get("/api/claims").with(officer1())), 403);                         // "my claims" is claimants'
        problem(act(manager(), "/api/staff/claims/" + id + "/assign", ""), 403);               // manager is read-only
        mvc.perform(get("/api/staff/claims/queue").with(manager())).andExpect(status().isOk());
    }

    // ---------- record-level access ----------

    @Test
    void claimantsCannotSeeEachOthersClaims() throws Exception {
        String id = submit();                                                                   // owned by tan@
        mvc.perform(get("/api/claims/" + id).with(claimant())).andExpect(status().isOk());
        problem(mvc.perform(get("/api/claims/" + id).with(otherClaimant())), 404);              // no enumeration
        problem(mvc.perform(get("/api/claims/" + id + "/history").with(otherClaimant())), 404);
        mvc.perform(get("/api/claims").with(otherClaimant())).andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").doesNotExist());
        mvc.perform(get("/api/claims/" + id).with(officer1())).andExpect(status().isOk());      // staff can read any claim
    }

    @Test
    void officersSeeOnlyTheirOwnWorkloadManagersSeeAll() throws Exception {
        String id = submit();
        act(officer1(), "/api/staff/claims/" + id + "/assign", "").andExpect(status().isOk());
        mvc.perform(get("/api/staff/claims").with(officer1())).andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").exists());
        mvc.perform(get("/api/staff/claims").with(officer2())).andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").doesNotExist());
        problem(mvc.perform(get("/api/staff/claims?officerId=officer-1").with(officer2())), 403);
        mvc.perform(get("/api/staff/claims?officerId=officer-1").with(manager()))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").exists());
    }

    // ---------- reassignment ----------

    @Test
    void owningOfficerOrManagerCanReassignOthersCannot() throws Exception {
        String id = submit();
        act(officer1(), "/api/staff/claims/" + id + "/assign", "").andExpect(status().isOk());

        problem(act(officer2(), "/api/staff/claims/" + id + "/reassign", "{\"toOfficerId\":\"officer-2\"}"), 422);
        act(officer1(), "/api/staff/claims/" + id + "/reassign", "{\"toOfficerId\":\"officer-2\",\"reason\":\"holiday\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedOfficerId").value("officer-2"))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        problem(act(officer2(), "/api/staff/claims/" + id + "/reassign", "{\"toOfficerId\":\"officer-2\"}"), 422);   // already theirs
        act(manager(), "/api/staff/claims/" + id + "/reassign", "{\"toOfficerId\":\"officer-1\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedOfficerId").value("officer-1"));
        // the previous owner can no longer act; the new owner can
        act(officer2(), "/api/staff/claims/" + id + "/assess", "{\"assessedAmount\":10}").andExpect(status().isUnprocessableEntity());
        act(officer1(), "/api/staff/claims/" + id + "/assess", "{\"assessedAmount\":10}").andExpect(status().isOk());
        mvc.perform(get("/api/claims/" + id + "/history").with(claimant()))
                .andExpect(jsonPath("$[?(@.note =~ /Reassigned from officer-1 to officer-2.*/)]").exists());
    }

    // ---------- documents ----------

    private static final byte[] PDF = "%PDF-1.4 test document".getBytes(StandardCharsets.US_ASCII);

    private ResultActions upload(RequestPostProcessor user, String id, String name, String type, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/claims/" + id + "/documents").file(new MockMultipartFile("file", name, type, bytes))
                .with(user));
    }

    @Test
    void claimantUploadsListsAndDownloadsDocuments() throws Exception {
        String id = submit();
        String created = upload(claimant(), id, "../../evil path/police report.pdf", "application/pdf", PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("police report.pdf"))   // path stripped
                .andExpect(jsonPath("$.sizeBytes").value(PDF.length))
                .andReturn().getResponse().getContentAsString();
        String docId = json.readTree(created).get("id").asText();

        mvc.perform(get("/api/claims/" + id + "/documents").with(officer1())).andExpect(jsonPath("$.length()").value(1));
        byte[] downloaded = mvc.perform(get("/api/claims/" + id + "/documents/" + docId).with(officer1()))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PDF);
    }

    @Test
    void documentNegativeScenarios() throws Exception {
        String id = submit();
        problem(upload(claimant(), id, "x.exe", "application/x-msdownload", PDF), 415);                 // type not allowed
        problem(upload(claimant(), id, "fake.pdf", "application/pdf", "<html>not a pdf</html>".getBytes()), 415);   // sniffed
        problem(upload(claimant(), id, "empty.pdf", "application/pdf", new byte[0]), 400);
        problem(upload(otherClaimant(), id, "a.pdf", "application/pdf", PDF), 404);                      // not their claim
        problem(upload(officer1(), id, "a.pdf", "application/pdf", PDF), 403);                           // only claimants upload
        problem(mvc.perform(get("/api/claims/" + id + "/documents/" + UUID.randomUUID()).with(claimant())), 404);
        problem(mvc.perform(get("/api/claims/" + id + "/documents").with(otherClaimant())), 404);
        problem(upload(claimant(), id, "big.pdf", "application/pdf", bigPdf()), 413);                    // > 5 MB
    }

    @Test
    void documentsCannotBeAddedToClosedClaims() throws Exception {
        String id = submit();
        act(officer1(), "/api/staff/claims/" + id + "/assign", "").andExpect(status().isOk());
        act(officer1(), "/api/staff/claims/" + id + "/reject", "{\"reason\":\"r\"}").andExpect(status().isOk());
        problem(upload(claimant(), id, "late.pdf", "application/pdf", PDF), 409);
    }

    private static byte[] bigPdf() {
        byte[] bytes = new byte[5 * 1024 * 1024 + 10];
        System.arraycopy(PDF, 0, bytes, 0, PDF.length);
        return bytes;
    }
}
