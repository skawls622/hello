package egovframework.example.sample;

import egovframework.example.sample.service.SampleDefaultVO;
import egovframework.example.sample.service.SampleVO;
import egovframework.example.sample.service.impl.SampleMapper;
import java.io.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.*;
import static org.junit.Assert.*;

/** Run with -Dtest.jdbc.properties=/path/to/jdbc.properties. All test changes roll back. */
public class MapperIntegrationTest {
    private SqlSession session;
    private SampleMapper mapper;
    private String prefix;

    @Before public void open() throws Exception {
        String path = System.getProperty("test.jdbc.properties");
        Assume.assumeTrue("Supply test.jdbc.properties for a real DB integration test", path != null);
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(path)) { p.load(in); }
        UnpooledDataSource ds = new UnpooledDataSource(p.getProperty("jdbc.driver"), p.getProperty("jdbc.url"),
                p.getProperty("jdbc.username"), p.getProperty("jdbc.password"));
        Configuration cfg = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
        Properties vendors = new Properties();
        vendors.setProperty("MySQL", "mysql"); vendors.setProperty("MariaDB", "mariadb"); vendors.setProperty("Oracle", "oracle");
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider(); provider.setProperties(vendors);
        cfg.setDatabaseId(provider.getDatabaseId(ds));
        cfg.getTypeAliasRegistry().registerAlias("sampleVO", SampleVO.class);
        cfg.getTypeAliasRegistry().registerAlias("searchVO", SampleDefaultVO.class);
        String resource = "egovframework/sqlmap/example/mappers/EgovSample_Sample_SQL.xml";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, cfg, resource, cfg.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(cfg).openSession(false);
        mapper = session.getMapper(SampleMapper.class);
        prefix = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        mapper.insertSample(row(prefix + "1", prefix + " Alpha"));
        mapper.insertSample(row(prefix + "2", prefix + " Beta"));
        mapper.insertSample(row(prefix + "3", prefix + " Alpha"));
    }
    private SampleVO row(String id, String name) {
        SampleVO vo = new SampleVO(); vo.setId(id); vo.setName(name); vo.setUseYn("Y"); vo.setRegUser("test");
        return vo;
    }
    private SampleDefaultVO separate(String id, String name) {
        SampleDefaultVO vo = new SampleDefaultVO(); vo.setSearchCondition("2");
        vo.setSearchId(id); vo.setSearchName(name); vo.setFirstIndex(0); vo.setRecordCountPerPage(20); return vo;
    }
    @After public void rollback() { if (session != null) { session.rollback(); session.close(); } }

    @Test public void separateIdAndNameUseAndAndShareCount() throws Exception {
        SampleDefaultVO q = separate(prefix, "Alpha");
        assertEquals(2, mapper.selectSampleListTotCnt(q));
        assertEquals(2, mapper.selectSampleList(q).size());
        q.setSearchId(prefix + "2");
        assertEquals(0, mapper.selectSampleListTotCnt(q));
        assertTrue(mapper.selectSampleList(q).isEmpty());
    }
    @Test public void eachFieldAloneAndWhitespaceAreOptional() throws Exception {
        SampleDefaultVO q = separate("  " + prefix + "  ", "   ");
        assertEquals(3, mapper.selectSampleListTotCnt(q));
        q.setSearchId(null); q.setSearchName(prefix + " Beta");
        assertEquals(1, mapper.selectSampleList(q).size());
    }
    @Test public void legacySearchModesBindParameters() throws Exception {
        SampleDefaultVO q = separate(null, null); q.setSearchCondition("0"); q.setSearchKeyword(prefix + "1");
        assertEquals(1, mapper.selectSampleListTotCnt(q));
        q.setSearchCondition("1"); q.setSearchKeyword(prefix + " Alpha");
        assertEquals(2, mapper.selectSampleListTotCnt(q));
        q.setSearchKeyword("' OR 1=1 --");
        assertEquals(0, mapper.selectSampleListTotCnt(q));
    }
    @Test public void paginationIsStableAndDoesNotChangeTotal() throws Exception {
        SampleDefaultVO q = separate(prefix, null); q.setRecordCountPerPage(2);
        List<?> page1 = mapper.selectSampleList(q);
        assertEquals(2, page1.size()); assertEquals(prefix + "3", ((SampleVO) page1.get(0)).getId());
        q.setFirstIndex(2); List<?> page2 = mapper.selectSampleList(q);
        assertEquals(1, page2.size()); assertEquals(prefix + "1", ((SampleVO) page2.get(0)).getId());
        assertEquals(3, mapper.selectSampleListTotCnt(q));
    }
    @Test public void crudSupportsNullDescriptionAndLombokAccessors() throws Exception {
        SampleVO vo = row(prefix + "4", "한글 이름"); mapper.insertSample(vo);
        SampleVO found = mapper.selectSample(vo); assertEquals("한글 이름", found.getName()); assertNull(found.getDescription());
        vo.setName("changed"); vo.setDescription("수정"); mapper.updateSample(vo);
        assertEquals("수정", mapper.selectSample(vo).getDescription());
        mapper.deleteSample(vo); assertNull(mapper.selectSample(vo));
    }
}
