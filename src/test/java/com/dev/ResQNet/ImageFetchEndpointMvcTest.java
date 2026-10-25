package com.dev.ResQNet;

import java.io.ByteArrayInputStream;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mongodb.client.gridfs.model.GridFSFile;

@WebMvcTest(imageFetchController.class)
@Import({SecurityConfig.class, TestCacheConfiguration.class})
class ImageFetchEndpointMvcTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MongoDatabaseFactory mongoDatabaseFactory;

    @MockitoBean
    private GridFsTemplate gridFsTemplate;

    @MockitoBean
    private disasterController disasterController;

    @Test
    @WithMockUser(roles = "ADMIN")
    void imageEndpointReturnsStoredBytesAndContentTypeForAdmin() throws Exception {
        ObjectId imageId = new ObjectId();
        byte[] imageBytes = new byte[]{1, 2, 3, 4};

        GridFSFile file = org.mockito.Mockito.mock(GridFSFile.class);
        GridFsResource resource = org.mockito.Mockito.mock(GridFsResource.class);

        when(gridFsTemplate.findOne(any(Query.class))).thenReturn(file);
        when(gridFsTemplate.getResource(file)).thenReturn(resource);
        when(resource.getContentType()).thenReturn("image/png");
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(imageBytes));

        mvc.perform(get("/fetch/image/{imageId}", imageId.toHexString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(imageBytes));

        verify(gridFsTemplate).findOne(any(Query.class));
        verify(gridFsTemplate).getResource(file);
    }

    @Test
    void imageEndpointRejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/fetch/image/{imageId}", new ObjectId().toHexString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void imageEndpointRejectsNonAdminUser() throws Exception {
        mvc.perform(get("/fetch/image/{imageId}", new ObjectId().toHexString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void nonExistentImageReturnsNotFound() throws Exception {
        when(gridFsTemplate.findOne(any(Query.class))).thenReturn(null);

        mvc.perform(get("/fetch/image/{imageId}", new ObjectId().toHexString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void malformedImageIdIsRejected() throws Exception {
        mvc.perform(get("/fetch/image/not-an-object-id"))
                .andExpect(status().isBadRequest());
    }
}
