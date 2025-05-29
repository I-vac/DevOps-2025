package minitwit;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.ext.beans.BeansWrapperBuilder;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.TemplateModelException;

import java.io.StringWriter;
import java.io.IOException;
import java.util.Map;

public class TemplateRenderer {
    private static final Configuration freemarkerConfig =
        new Configuration(Configuration.VERSION_2_3_31);

    /**
     * Call once at application startup to configure FreeMarker:
     * sets template loading, object wrapper, and shared variables.
     */
    public static void configure() {
        // Load templates from the classpath /templates directory
        freemarkerConfig.setClassForTemplateLoading(
            TemplateRenderer.class,
            "/templates"
        );
        freemarkerConfig.setDefaultEncoding("UTF-8");

        // Enable beans and static models
        freemarkerConfig.setObjectWrapper(
            new DefaultObjectWrapperBuilder(
                Configuration.VERSION_2_3_31
            ).build()
        );

        try {
            BeansWrapper wrapper = new BeansWrapperBuilder(
                Configuration.VERSION_2_3_31
            ).build();

            // Expose util classes in templates
            freemarkerConfig.setSharedVariable(
                "GravatarUtil",
                wrapper.getStaticModels().get(
                    "minitwit.util.GravatarUtil"
                )
            );
            freemarkerConfig.setSharedVariable(
                "DateUtil",
                wrapper.getStaticModels().get(
                    "minitwit.util.DateUtil"
                )
            );
        } catch (TemplateModelException e) {
            throw new RuntimeException(
                "Failed to register FreeMarker shared variables",
                e
            );
        }
    }

    /**
     * Render the given template name with the provided model.
     */
    public static String render(String templateName, Map<String, Object> model) {
        try (StringWriter writer = new StringWriter()) {
            Template template = freemarkerConfig.getTemplate(
                templateName + ".ftl"
            );
            template.process(model, writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            throw new RuntimeException(
                "Failed to render template: " + templateName,
                e
            );
        }
    }
}