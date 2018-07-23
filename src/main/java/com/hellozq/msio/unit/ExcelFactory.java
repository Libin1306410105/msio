package com.hellozq.msio.unit;

import com.esotericsoftware.reflectasm.MethodAccess;
import com.hellozq.msio.bean.common.IFormatConversion;
import com.hellozq.msio.config.MsIoContainer;
import com.hellozq.msio.exception.IndexOutOfSheetSizeException;
import com.hellozq.msio.exception.UnsupportFormatException;
import com.hellozq.msio.utils.ClassUtils;
import com.hellozq.msio.utils.MsUtils;
import com.hellozq.msio.utils.SpringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.validation.constraints.NotNull;
import java.io.InputStream;
import java.util.*;

/**
 * @author bin
 * excel文件的处理工厂
 */
@SuppressWarnings("unused")
public class ExcelFactory {

    private static final Log log = LogFactory.getLog(ExcelFactory.class);

    public SimpleExcelBean getSimpleInstance(Class<?> generateClass, boolean isTuring,@NotNull InputStream file){
        return new SimpleExcelBean(generateClass,isTuring,file);
    }

    /**
     * 设置方法
     * @param i 输入链接
     */

    private Workbook setWorkbook(@NotNull InputStream i){
        Workbook workbook;
        try {
            workbook = new XSSFWorkbook(i);
        }catch (Exception e){
            try{
                workbook = new HSSFWorkbook(i);
            }catch (Exception e1){
                e1.printStackTrace();
                throw new IllegalArgumentException("文件格式不符合，无法加入");
            }
        }
        return workbook;
    }

    /**
     * 简单excel实例单元
     * 简单Excel：没有复杂的合并单元格选项，一比一对比
     */
    private final class SimpleExcelBean{
        /**
         * 是否需要自动翻页
         */
        private boolean isTuring = true;
        /**
         * 是否根据自动更新Class
         */
        private boolean isChangeClass = false;

        private Class<?> clazz;

        private Workbook workbook;

        private Map<Integer, List> dataCache;

        private MsIoContainer msIoContainer;

        private IFormatConversion formatConversion;

        /**
         * 初始化
         * @param generateClass 指派导出类型，为null则自行查询
         * @param isTuring 是否自动迭代页面,自动迭代时导出类型无效
         * @param file 文件流
         */
        private SimpleExcelBean(Class<?> generateClass, boolean isTuring, InputStream file){
            this.clazz = generateClass;
            this.isTuring = isTuring;
            this.workbook = setWorkbook(file);
            msIoContainer = SpringUtils.getBean(MsIoContainer.class);
            this.formatConversion = SpringUtils.getBean(IFormatConversion.class);

        }

        /**
         * 获取总页数
         * @return 页数
         */
        protected int getPageSize(){
            return workbook.getNumberOfSheets();
        }

        @SuppressWarnings("unchecked")
        protected List getPageContent(int pageIndex) throws IndexOutOfSheetSizeException,UnsupportFormatException{

            if(getPageSize() <= pageIndex){
                throw new IndexOutOfSheetSizeException("页码最大值为"+getPageSize()+"的数据，强行获取"+pageIndex+"页数据");
            }
            Sheet sheetNow = workbook.getSheetAt(pageIndex);
            int regionNum = sheetNow.getNumMergedRegions();
            //初始行
            int rowIndex = 0;
            if(regionNum > 1){
                throw new UnsupportFormatException("当前模式不支持多个合并单元格格式的解析，请切换解析方式为复杂方式");
            }
            //标题切除
            if(regionNum == 1){
                CellRangeAddress mergedRegion = sheetNow.getMergedRegion(0);
                if(mergedRegion.getFirstRow() != 0){
                    throw new UnsupportFormatException("当前模式仅支持首行标题合并解析，请切换解析方式为复杂模式");
                }
                rowIndex = mergedRegion.getLastRow() + 1;
            }
            //正式解析
            List<String> titles = MsUtils.getRowDataInString(rowIndex, 0, 0, sheetNow);
            if(titles == null || titles.size() == 0){
                throw new NullPointerException("标题行为空，请检查格式");
            }
            LinkedHashMap<String, MsIoContainer.Information> mapping;
            //若clazz为null，则自动匹配
            if(clazz == null || isChangeClass){
                String match = msIoContainer.match(titles);
                mapping = msIoContainer.get(match);
                clazz = msIoContainer.getClazz(match);
            }else{
                mapping = msIoContainer.get(clazz);
            }
            LinkedHashMap<String, String> inversion = MsUtils.mapInversion(mapping);
            if(inversion.isEmpty()){
                titles.forEach(s -> inversion.put(s,s));
            }
            List list = new ArrayList();
            if(clazz == Map.class){
                for (int i = rowIndex; i < sheetNow.getLastRowNum(); i++) {
                    list.add(conversionMap(sheetNow.getRow(rowIndex), inversion, titles));
                }
            }else{
                for (int i = rowIndex; i < sheetNow.getLastRowNum(); i++) {
                    list.add(conversionPojo(sheetNow.getRow(rowIndex), inversion, titles, clazz, mapping));
                }
            }
            return list;
        }

        /**
         * 内置工具方法，获取当前行的解析结果
         * @param row 待处理数据
         * @param inversion 映射数据
         * @param titles 提取出的标题数据
         * @return 解析结果
         */
        private Map<String,String> conversionMap(Row row, LinkedHashMap<String, String> inversion, List<String> titles){
            Map<String, String> result = new HashMap<>(16);
            for (int i = 0; i < titles.size(); i++) {
                String value = MsUtils.getStringValueFromCell(row.getCell(i));
                result.put(inversion.get(titles.get(i)),value);
            }
            return result;
        }

        /**
         * 内置工具方法，获取当前行的解析Pojo结果
         * @param row 待处理行数据
         * @param inversion 映射数据
         * @param titles 提取出来的标题数据
         * @param clazz Pojo对应类
         * @param auto 自动赋值方法
         * @return 解析结果
         */
        private Object conversionPojo(Row row, LinkedHashMap<String, String> inversion, List<String> titles, Class<?> clazz,
                                      LinkedHashMap<String,MsIoContainer.Information> auto) throws NoSuchMethodException{
            Object obj = clazz.newInstance();
            for (int i = 0; i < titles.size(); i++) {
                String value = MsUtils.getStringValueFromCell(row.getCell(i));
                String title = titles.get(i);
                String egTitle = inversion.get(title);
                MsIoContainer.Information information = auto.get(title);
                if(information.getFieldType() == String.class){
                    ClassUtils.setFieldValue(value, egTitle, obj, clazz);
                }else{
                    String simpleName = "fromStringto" + information.getFieldType().getSimpleName();
                    MethodAccess methodAccess = ClassUtils.getMethodAccess(formatConversion.getClass());
                    int methodIndex = 0;
                    try {
                        methodIndex = methodAccess.getIndex(simpleName, String.class);
                    }catch (IllegalArgumentException e){
                        log.error("尝试使用" + simpleName + "获取方法失败，正在尝试使用全名获取");
                        String flexName = "fromStringto" + information.getFieldType().getName().replaceAll(".", "");
                        try {
                            methodIndex = methodAccess.getIndex(flexName, String.class);
                        }catch (IllegalArgumentException e1){
                            log.error("尝试使用" + flexName + "获取方法失败，抛出异常，请检查是否存在方法或者方法是否设置为non-private");
                            throw new NoSuchMethodException("无法找到方法" + simpleName + "、" + flexName);
                        }
                    }
                    Object invoke = methodAccess.invoke(formatConversion, methodIndex, String.class, value);
                    ClassUtils.setFieldValue(value, egTitle, obj, clazz);
                }
            }
        }
    }

    /**
     * 复杂excel实例单元
     * 复杂Excel：格式复杂，有合并单元格选项，需要筛选
     */
    private final class FlexExcelBean{

    }


}
