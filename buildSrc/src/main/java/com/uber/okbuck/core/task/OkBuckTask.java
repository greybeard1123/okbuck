package com.uber.okbuck.core.task;

import com.uber.okbuck.OkBuckGradlePlugin;
import com.uber.okbuck.core.model.base.ProjectType;
import com.uber.okbuck.core.util.FileUtil;
import com.uber.okbuck.core.util.GroovyUtil;
import com.uber.okbuck.core.util.KotlinUtil;
import com.uber.okbuck.core.util.ProguardUtil;
import com.uber.okbuck.core.util.ProjectUtil;
import com.uber.okbuck.core.util.ScalaUtil;
import com.uber.okbuck.extension.OkBuckExtension;
import com.uber.okbuck.extension.ScalaExtension;
import com.uber.okbuck.generator.BuckConfigLocalGenerator;
import com.uber.okbuck.template.config.BuckDefs;

import org.gradle.api.DefaultTask;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static com.uber.okbuck.OkBuckGradlePlugin.OKBUCK_DEFS;

@SuppressWarnings({"WeakerAccess", "CanBeFinal", "unused", "ResultOfMethodCallIgnored", "NewApi"})
public class OkBuckTask extends DefaultTask {

    @Nested
    public OkBuckExtension okBuckExtension;

    @Nested
    public ScalaExtension scalaExtension;

    public OkBuckTask() {
        // Never up to date; this task isn't safe to run incrementally.
        getOutputs().upToDateWhen(Specs.satisfyNone());
    }

    @TaskAction
    void okbuck() {
        // Fetch Groovy support deps if needed
        boolean hasGroovyLib = okBuckExtension.buckProjects.stream().anyMatch(
                project -> ProjectUtil.getType(project) == ProjectType.GROOVY_LIB);
        if (hasGroovyLib) {
            GroovyUtil.setupGroovyHome(getProject());
        }

        // Fetch Kotlin support deps if needed
        boolean hasKotlinLib = KotlinUtil.hasKotlinPluginInClasspath(getProject());
        if (hasKotlinLib) {
            KotlinUtil.setupKotlinHome(getProject());
        }

        // Fetch Scala support deps if needed
        boolean hasScalaLib = okBuckExtension.buckProjects.stream().anyMatch(
                project -> ProjectUtil.getType(project) == ProjectType.SCALA_LIB);
        if (hasScalaLib) {
            ScalaUtil.setupScalaHome(getProject(), scalaExtension.version);
        }

        generate(okBuckExtension,
                hasGroovyLib ? GroovyUtil.GROOVY_HOME_LOCATION : null,
                hasKotlinLib ? KotlinUtil.KOTLIN_HOME_LOCATION : null,
                hasScalaLib ? ScalaUtil.SCALA_HOME_LOCATION : null);

        // Perform dependency cache cleanup and persistence
        ProjectUtil.getDependencyCache(getProject()).finalizeDeps();
    }

    @Override
    public String getGroup() {
        return OkBuckGradlePlugin.GROUP;
    }

    @Override
    public String getDescription() {
        return "Okbuck task for the root project. Also sets up groovy and kotlin if required.";
    }

    @OutputFile
    public File okbuckDefs() {
        return getProject().file(OKBUCK_DEFS);
    }

    @OutputFile
    public File dotBuckConfig() {
        return getProject().file(".buckconfig");
    }

    @OutputFile
    public File dotBuckConfigLocal() {
        return getProject().file(".buckconfig.local");
    }

    private void generate(
            OkBuckExtension okbuckExt,
            @Nullable String groovyHome,
            @Nullable String kotlinHome,
            @Nullable String scalaHome) {
        // generate empty .buckconfig if it does not exist
        try {
            dotBuckConfig().createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Setup defs
        new BuckDefs().resourceExcludes(okBuckExtension.excludeResources
                .stream()
                .map(s -> "'" + s + "'")
                .collect(Collectors.toSet())).render(okbuckDefs());
        Set<String> defs = okbuckExt.extraDefs.stream()
                .map(it -> "//" + FileUtil.getRelativePath(getProject().getRootDir(), it))
                .collect(Collectors.toSet());
        defs.add("//" + OKBUCK_DEFS);

        // generate .buckconfig.local
        BuckConfigLocalGenerator.generate(okbuckExt,
                groovyHome,
                kotlinHome,
                scalaHome,
                ProguardUtil.getProguardJarPath(getProject()),
                defs).render(dotBuckConfigLocal());
    }
}
