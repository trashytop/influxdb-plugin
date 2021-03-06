package jenkinsci.plugins.influxdb.generators;

import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.PerfPublisher.PerfPublisherBuildAction;
import hudson.plugins.PerfPublisher.Report.Metric;
import hudson.plugins.PerfPublisher.Report.Report;
import hudson.plugins.PerfPublisher.Report.ReportContainer;
import jenkins.model.Jenkins;
import jenkinsci.plugins.influxdb.renderer.MeasurementRenderer;
import jenkinsci.plugins.influxdb.renderer.ProjectNameRenderer;
import org.influxdb.dto.Point;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

/**
 * @author Eugene Schava <eschava@gmail.com>
 */
public class PerfPublisherPointGeneratorTest {

    private static final String JOB_NAME = "master";
    private static final int BUILD_NUMBER = 11;
    private static final String CUSTOM_PREFIX = "test_prefix";

    private Run<?,?> build;
    private MeasurementRenderer<Run<?, ?>> measurementRenderer;
    private ReportContainer reports;

    private long currTime;

    @Before
    public void before() {
        build = Mockito.mock(Run.class);
        Job job = Mockito.mock(Job.class);
        measurementRenderer = new ProjectNameRenderer(CUSTOM_PREFIX, null);
        PerfPublisherBuildAction buildAction = Mockito.mock(PerfPublisherBuildAction.class);
        reports = new ReportContainer();

        Mockito.when(build.getNumber()).thenReturn(BUILD_NUMBER);
        Mockito.when(build.getParent()).thenReturn(job);
        Mockito.when(job.getName()).thenReturn(JOB_NAME);
        Mockito.when(job.getRelativeNameFrom(Mockito.nullable(Jenkins.class))).thenReturn("folder/" + JOB_NAME);
        Mockito.when(build.getAction(PerfPublisherBuildAction.class)).thenReturn(buildAction);

        Mockito.when(buildAction.getReport()).thenAnswer(new Answer<Report>() {
            @Override
            public Report answer(InvocationOnMock invocationOnMock) {
                return reports.getReports().isEmpty() ? null : reports.getReports().get(0);
            }
        });
        Mockito.when(buildAction.getReports()).thenReturn(reports);

        currTime = System.currentTimeMillis();
    }

    @Test
    public void hasReport() {
        PerfPublisherPointGenerator generator = new PerfPublisherPointGenerator(measurementRenderer, CUSTOM_PREFIX, build, currTime);
        assertThat(generator.hasReport(), is(false));

        reports.addReport(new Report());
        generator = new PerfPublisherPointGenerator(measurementRenderer, CUSTOM_PREFIX, build, currTime);
        assertThat(generator.hasReport(), is(true));
    }

    @Test
    public void generate() {
        Report report = new Report();

        hudson.plugins.PerfPublisher.Report.Test test = new hudson.plugins.PerfPublisher.Report.Test();
        test.setName("test.txt");
        test.setExecuted(true);

        Map<String, Metric> metrics = new HashMap<>();
        Metric metric1 = new Metric();
        metric1.setMeasure(50);
        metric1.setRelevant(true);
        metric1.setUnit("ms");
        metrics.put("metric1", metric1);
        test.setMetrics(metrics);

        report.addTest(test);
        reports.addReport(report);
        PerfPublisherPointGenerator generator = new PerfPublisherPointGenerator(measurementRenderer, CUSTOM_PREFIX, build, currTime);
        Point[] points = generator.generate();

        assertThat(points[0].lineProtocol(), startsWith("perfpublisher_summary,prefix=test_prefix,project_name=test_prefix_master,project_path=folder/master build_number=11i,number_of_executed_tests=1i"));
        assertThat(points[1].lineProtocol(), startsWith("perfpublisher_metric,prefix=test_prefix,project_name=test_prefix_master,project_path=folder/master average=50.0,best=50.0,build_number=11i,metric_name=\"metric1\",project_name=\"test_prefix_master\",project_path=\"folder/master\",worst=50.0"));
        assertThat(points[2].lineProtocol(), startsWith("perfpublisher_test,prefix=test_prefix,project_name=test_prefix_master,project_path=folder/master,test_name=test.txt build_number=11i,executed=true,project_name=\"test_prefix_master\",project_path=\"folder/master\",successful=false,test_name=\"test.txt\""));
        assertThat(points[3].lineProtocol(), startsWith("perfpublisher_test_metric,prefix=test_prefix,project_name=test_prefix_master,project_path=folder/master,test_name=test.txt build_number=11i,metric_name=\"metric1\",project_name=\"test_prefix_master\",project_path=\"folder/master\",relevant=true,test_name=\"test.txt\",unit=\"ms\",value=50.0"));
    }
}
