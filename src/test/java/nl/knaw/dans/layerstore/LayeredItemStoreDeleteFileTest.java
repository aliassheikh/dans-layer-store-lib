/*
 * Copyright (C) 2024 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.layerstore;

import io.dropwizard.util.DirectExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static nl.knaw.dans.layerstore.TestUtils.assumeNotYetFixed;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class LayeredItemStoreDeleteFileTest extends AbstractLayerDatabaseTest {

    @BeforeEach
    public void prepare() throws Exception {
        Files.createDirectories(stagingDir);
    }

    @Test
    public void should_not_delete_a_file_in_a_closed_layer() throws Exception {
        var layerManager = new LayerManagerImpl(stagingDir, new ZipArchiveProvider(archiveDir), new DirectExecutorService());
        var layeredStore = new LayeredItemStore(db, layerManager);
        Files.createDirectories(archiveDir);
        layeredStore.createDirectory("a/b/c/d");
        layeredStore.writeFile("a/b/c/d/test1.txt", toInputStream("Hello world!", UTF_8));
        layeredStore.writeFile("a/b/c/test2.txt", toInputStream("Hello again!", UTF_8));
        var firstLayer = layerManager.getTopLayer();
        layerManager.newTopLayer();
        assertFalse(firstLayer.isOpen());

        assumeNotYetFixed("getLayer returns a new layer object. New layer objects are open but it should be closed in this scenario.");
        assertThatThrownBy(() -> layeredStore.deleteFiles(List.of("a/b/c/d/test1.txt", "a/b/c/test2.txt")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot delete files from closed layer");
    }

    @Test
    public void should_delete_files_from_the_top_layer() throws Exception {
        var layerManager = new LayerManagerImpl(stagingDir, new ZipArchiveProvider(archiveDir), new DirectExecutorService());
        var layeredStore = new LayeredItemStore(db, layerManager);
        layeredStore.createDirectory("a/b/c/d");
        layeredStore.writeFile("a/b/c/d/test1.txt", toInputStream("Hello world!", UTF_8));
        layeredStore.writeFile("a/b/c/test2.txt", toInputStream("Hello world!", UTF_8));

        // precondition: show database content
        var list1 = daoTestExtension.inTransaction(() ->
            db.getAllRecords().toList().stream().map(ItemRecord::getPath)
        );
        assertThat(list1).containsExactlyInAnyOrder("", "a", "a/b", "a/b/c", "a/b/c/d", "a/b/c/d/test1.txt", "a/b/c/test2.txt");

        // method under test
        layeredStore.deleteFiles(List.of("a/b/c/d/test1.txt", "a/b/c/test2.txt"));

        // files are removed from the stagingDir
        Path layerDir = stagingDir.resolve(Path.of(String.valueOf((layerManager.getTopLayer().getId()))));
        assertThat(layerDir.resolve("a/b/c/d/test1.txt")).doesNotExist();
        assertThat(layerDir.resolve("a/b/c/test2.txt")).doesNotExist();

        // files are removed from the database
        var list2 = daoTestExtension.inTransaction(() ->
            db.getAllRecords().toList().stream().map(ItemRecord::getPath)
        );
        assertThat(list2).containsExactlyInAnyOrder("", "a", "a/b", "a/b/c", "a/b/c/d");
    }
}