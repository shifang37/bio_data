<template>
  <div class="csv-importer">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>CSV文件批量导入</span>
          <el-button v-if="!importing" type="primary" @click="resetImporter">重置</el-button>
        </div>
      </template>

      <!-- 步骤指示器 -->
      <el-steps :active="currentStep" finish-status="success" align-center>
        <el-step title="选择文件" description="选择要导入的CSV文件"></el-step>
        <el-step title="预览数据" description="预览和验证数据格式"></el-step>
        <el-step title="导入数据" description="执行批量数据导入"></el-step>
      </el-steps>

      <div style="margin-top: 20px;">
        <!-- 步骤1：文件选择 -->
        <div v-if="currentStep === 0">
          <el-upload
            class="upload-demo"
            drag
            :auto-upload="false"
            :on-change="handleFileChange"
            :file-list="fileList"
            :limit="1"
            accept=".csv"
          >
            <el-icon class="el-icon--upload">
              <UploadFilled />
            </el-icon>
            <div class="el-upload__text">
              将CSV文件拖到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                只能上传CSV文件，且文件大小不能超过100MB
              </div>
            </template>
          </el-upload>

          <div v-if="fileList.length > 0" style="margin-top: 20px;">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="目标数据库">
                  <el-select v-model="selectedDataSource" placeholder="选择目标数据库">
                    <el-option
                      v-for="item in dataSources"
                      :key="item.name"
                      :label="item.displayName"
                      :value="item.name"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="目标表">
                  <el-select v-model="selectedTable" placeholder="选择目标表" @change="loadTableColumns">
                    <el-option
                      v-for="item in tables"
                      :key="item.TABLE_NAME"
                      :label="item.TABLE_NAME"
                      :value="item.TABLE_NAME"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="编码格式">
                  <el-select v-model="encoding" placeholder="选择编码格式">
                    <el-option label="UTF-8" value="utf-8" />
                    <el-option label="GBK" value="gbk" />
                    <el-option label="ASCII" value="ascii" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="分隔符">
                  <el-select v-model="delimiter" placeholder="选择分隔符">
                    <el-option label="逗号 (,)" value="," />
                    <el-option label="分号 (;)" value=";" />
                    <el-option label="制表符 (Tab)" value="\t" />
                    <el-option label="竖线 (|)" value="|" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item>
              <el-checkbox v-model="hasHeader">文件包含标题行</el-checkbox>
            </el-form-item>

            <el-button 
              type="primary" 
              @click="parseFile" 
              :disabled="!selectedDataSource || !selectedTable"
            >
              解析文件
            </el-button>
          </div>
        </div>

        <!-- 步骤2：数据预览 -->
        <div v-if="currentStep === 1">
          <div style="margin-bottom: 20px;">
            <el-alert
              title="数据预览"
              :description="`解析到 ${parsedData.length} 条记录，显示前 ${Math.min(10, parsedData.length)} 条`"
              type="info"
              show-icon
            />
          </div>

          <!-- 列映射 -->
          <div v-if="showColumnMapping" style="margin-bottom: 20px;">
            <h4>列映射配置</h4>
            <el-table :data="columnMapping" style="width: 100%">
              <el-table-column prop="csvColumn" label="CSV列" width="200" />
              <el-table-column prop="dbColumn" label="数据库列" width="200">
                <template #default="scope">
                  <el-select 
                    v-model="scope.row.dbColumn" 
                    placeholder="选择数据库列"
                    clearable
                  >
                    <el-option
                      v-for="col in tableColumns"
                      :key="col.COLUMN_NAME"
                      :label="col.COLUMN_NAME"
                      :value="col.COLUMN_NAME"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column prop="dataType" label="数据类型" width="150" />
              <el-table-column prop="required" label="必填" width="80">
                <template #default="scope">
                  <el-tag v-if="scope.row.required" type="danger" size="small">必填</el-tag>
                  <el-tag v-else type="success" size="small">可选</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="sample" label="示例数据" />
            </el-table>
          </div>

          <!-- 数据预览表格 -->
          <el-table :data="previewData" style="width: 100%; margin-bottom: 20px;">
            <el-table-column 
              v-for="column in csvColumns" 
              :key="column" 
              :prop="column" 
              :label="column"
              min-width="120"
            />
          </el-table>

          <!-- 验证结果 -->
          <div v-if="validationResult" style="margin-bottom: 20px;">
            <el-alert
              v-if="validationResult.valid"
              title="数据验证通过"
              type="success"
              show-icon
            />
            <el-alert
              v-else
              title="数据验证失败"
              type="error"
              show-icon
            >
              <div v-for="error in validationResult.errors" :key="error">
                {{ error }}
              </div>
            </el-alert>
            
            <div v-if="validationResult.warnings && validationResult.warnings.length > 0" style="margin-top: 10px;">
              <el-alert
                title="数据验证警告"
                type="warning"
                show-icon
              >
                <div v-for="warning in validationResult.warnings" :key="warning">
                  {{ warning }}
                </div>
              </el-alert>
            </div>
          </div>

          <div>
            <el-button @click="currentStep = 0">上一步</el-button>
            <el-button 
              type="primary" 
              @click="validateAndProceed"
              :disabled="!columnMapping.length || !columnMapping.some(mapping => mapping.dbColumn)"
            >
              验证并继续
            </el-button>
          </div>
        </div>

        <!-- 步骤3：数据导入 -->
        <div v-if="currentStep === 2">
          <div style="margin-bottom: 20px;">
            <el-alert
              title="导入设置"
              :description="`即将导入 ${mappedData.length} 条记录到表 ${selectedTable}`"
              type="info"
              show-icon
            />
          </div>

          <el-form label-width="120px">
            <el-form-item label="导入模式">
              <el-radio-group v-model="importMode">
                <el-radio label="normal">普通模式（快速，但可能部分失败）</el-radio>
                <el-radio label="transaction">事务模式（安全，全部成功或全部失败）</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="批处理大小">
              <el-input-number v-model="batchSize" :min="1000" :max="10000" :step="1000" />
              <div style="color: #999; font-size: 12px; margin-top: 5px;">
                较大的批处理大小可以提高导入速度，但会占用更多内存
              </div>
            </el-form-item>
          </el-form>

          <!-- 导入进度 -->
          <div v-if="importing" style="margin-bottom: 20px;">
            <el-progress
              :percentage="importProgress"
              :status="importStatus"
              :stroke-width="20"
              text-inside
            />
            <div style="margin-top: 10px; text-align: center;">
              {{ importStatusText }}
            </div>
          </div>

          <!-- 导入结果 -->
          <div v-if="importResult" style="margin-bottom: 20px;">
            <el-alert
              :title="importResult.success ? '导入成功' : '导入失败'"
              :type="importResult.success ? 'success' : 'error'"
              show-icon
            >
              <div v-if="importResult.success">
                <p>总记录数: {{ importResult.result.totalRecords }}</p>
                <p>成功导入: {{ importResult.result.successCount }}</p>
                <p>失败记录: {{ importResult.result.failureCount }}</p>
                <p>耗时: {{ importResult.result.duration }}ms</p>
              </div>
              <div v-else>
                {{ importResult.error }}
              </div>
            </el-alert>

            <div v-if="importResult.result && importResult.result.errors && importResult.result.errors.length > 0" style="margin-top: 10px;">
              <el-collapse v-model="activeCollapse">
                <el-collapse-item title="错误详情" name="errors">
                  <div v-for="error in importResult.result.errors" :key="error" style="margin-bottom: 5px;">
                    {{ error }}
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>

          <div>
            <el-button @click="currentStep = 1" :disabled="importing">上一步</el-button>
            <el-button 
              type="primary" 
              @click="startImport"
              :disabled="importing || !mappedData.length"
              :loading="importing"
            >
              开始导入
            </el-button>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { ref, reactive, onMounted, computed, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import Papa from 'papaparse';
import { databaseApi } from '../utils/api';

export default {
  name: 'CsvImporter',
  components: {
    UploadFilled
  },
  props: {
    userId: {
      type: Number,
      required: true
    },
    userType: {
      type: String,
      required: true
    }
  },
  setup(props, { emit }) {
    const currentStep = ref(0);
    const fileList = ref([]);
    const selectedDataSource = ref('');
    const selectedTable = ref('');
    const encoding = ref('utf-8');
    const delimiter = ref(',');
    const hasHeader = ref(true);
    const parsedData = ref([]);
    const csvColumns = ref([]);
    const tableColumns = ref([]);
    const columnMapping = ref([]);
    const showColumnMapping = ref(false);
    const validationResult = ref(null);
    const mappedData = ref([]);
    const importMode = ref('normal');
    const batchSize = ref(5000);
    const importing = ref(false);
    const importProgress = ref(0);
    const importStatus = ref('');
    const importStatusText = ref('');
    const importResult = ref(null);
    const activeCollapse = ref([]);

    const dataSources = ref([]);
    const tables = ref([]);

    const previewData = computed(() => {
      return parsedData.value.slice(0, 10);
    });

    // 加载数据源列表
    const loadDataSources = async () => {
      try {
        // 使用新的API获取所有可用数据库（包括用户创建的）
        const response = await databaseApi.getAvailableDatabasesForImport(props.userId, props.userType);
        dataSources.value = response.data;
        if (dataSources.value.length > 0) {
          selectedDataSource.value = dataSources.value[0].name;
          loadTables();
        }
      } catch (error) {
        ElMessage.error('加载数据库列表失败: ' + error.message);
        
        // 如果新API失败，尝试使用旧的数据源API作为备选
        try {
          const fallbackResponse = await databaseApi.getAvailableDataSources();
          dataSources.value = fallbackResponse.data;
          if (dataSources.value.length > 0) {
            selectedDataSource.value = dataSources.value[0].name;
            loadTables();
          }
        } catch (fallbackError) {
          ElMessage.error('加载数据源失败: ' + fallbackError.message);
        }
      }
    };

    // 加载表列表
    const loadTables = async () => {
      if (!selectedDataSource.value) return;
      
      try {
        const response = await databaseApi.getAllTables(selectedDataSource.value, props.userId, props.userType);
        tables.value = response.data;
      } catch (error) {
        ElMessage.error('加载表列表失败: ' + error.message);
      }
    };

    // 加载表列信息
    const loadTableColumns = async () => {
      if (!selectedDataSource.value || !selectedTable.value) return;
      
      try {
        const response = await databaseApi.getTableColumnsInfo(
          selectedTable.value,
          selectedDataSource.value,
          props.userId,
          props.userType
        );
        tableColumns.value = response.data.columns;
        setupColumnMapping();
      } catch (error) {
        ElMessage.error('加载表列信息失败: ' + error.message);
      }
    };

    // 设置列映射
    const setupColumnMapping = () => {
      if (!csvColumns.value.length || !tableColumns.value.length) return;
      
      columnMapping.value = csvColumns.value.map(csvCol => {
        const dbCol = tableColumns.value.find(col => 
          col.COLUMN_NAME.toLowerCase() === csvCol.toLowerCase()
        );
        
        return {
          csvColumn: csvCol,
          dbColumn: dbCol ? dbCol.COLUMN_NAME : '',
          dataType: dbCol ? dbCol.DATA_TYPE : '',
          required: dbCol ? dbCol.isRequired : false,
          sample: parsedData.value.length > 0 ? parsedData.value[0][csvCol] : ''
        };
      });
      
      showColumnMapping.value = true;
    };

    // 处理文件选择
    const handleFileChange = (file) => {
      fileList.value = [file];
    };

    // 解析CSV文件
    const parseFile = () => {
      if (!fileList.value.length) {
        ElMessage.error('请先选择文件');
        return;
      }

      const file = fileList.value[0].raw;
      
      Papa.parse(file, {
        header: hasHeader.value,
        delimiter: delimiter.value,
        encoding: encoding.value,
        skipEmptyLines: true,
        complete: (results) => {
          if (results.errors.length > 0) {
            ElMessage.error('CSV解析错误: ' + results.errors[0].message);
            return;
          }
          
          parsedData.value = results.data;
          
          if (hasHeader.value) {
            csvColumns.value = results.meta.fields;
          } else {
            csvColumns.value = Object.keys(parsedData.value[0] || {});
          }
          
          if (parsedData.value.length === 0) {
            ElMessage.error('CSV文件为空');
            return;
          }
          
          ElMessage.success(`成功解析 ${parsedData.value.length} 条记录`);
          currentStep.value = 1;
          
          if (tableColumns.value.length > 0) {
            setupColumnMapping();
          }
        },
        error: (error) => {
          ElMessage.error('文件解析失败: ' + error.message);
        }
      });
    };

    // 验证数据并继续
    const validateAndProceed = async () => {
      // 映射数据
      mappedData.value = parsedData.value.map(row => {
        const mappedRow = {};
        columnMapping.value.forEach(mapping => {
          if (mapping.dbColumn && mapping.csvColumn) {
            mappedRow[mapping.dbColumn] = row[mapping.csvColumn];
          }
        });
        return mappedRow;
      });

      // 验证数据
      try {
        const response = await databaseApi.validateCsvData(
          selectedTable.value,
          {
            dataSource: selectedDataSource.value,
            dataList: mappedData.value,
            userId: props.userId,
            userType: props.userType
          }
        );
        
        validationResult.value = response.data.validation;
        
        if (validationResult.value.valid) {
          currentStep.value = 2;
        } else {
          ElMessage.error('数据验证失败，请检查错误信息');
        }
      } catch (error) {
        ElMessage.error('数据验证失败: ' + error.message);
      }
    };

    // 开始导入
    const startImport = async () => {
      if (!mappedData.value.length) {
        ElMessage.error('没有可导入的数据');
        return;
      }

      try {
        await ElMessageBox.confirm(
          `确定要导入 ${mappedData.value.length} 条记录到表 ${selectedTable.value} 吗？`,
          '确认导入',
          {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
          }
        );

        importing.value = true;
        importProgress.value = 0;
        importStatus.value = '';
        importStatusText.value = '正在导入数据...';

        const response = await databaseApi.batchInsertTableData(
          selectedTable.value,
          {
            dataSource: selectedDataSource.value,
            dataList: mappedData.value,
            useTransaction: importMode.value === 'transaction',
            userId: props.userId,
            userType: props.userType
          }
        );

        importResult.value = response.data;
        importProgress.value = 100;
        importStatus.value = importResult.value.success ? 'success' : 'exception';
        importStatusText.value = importResult.value.success ? '导入完成' : '导入失败';

        if (importResult.value.success) {
          ElMessage.success('数据导入成功');
          // 触发import-complete事件，通知父组件
          emit('import-complete', {
            ...importResult.value.result,
            dataSource: selectedDataSource.value,
            tableName: selectedTable.value
          });
        } else {
          ElMessage.error('数据导入失败');
          
          // 如果导入失败，自动进行诊断
          if (importResult.value.result) {
            await performDiagnosis();
          }
        }
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('导入失败: ' + error.message);
          importResult.value = {
            success: false,
            error: error.message
          };
          importStatus.value = 'exception';
          importStatusText.value = '导入失败';
          
          // 导入异常时也进行诊断
          await performDiagnosis();
        }
      } finally {
        importing.value = false;
      }
    };

    // 自动诊断功能
    const performDiagnosis = async () => {
      try {
        const response = await databaseApi.diagnoseTableForImport(
          selectedTable.value,
          selectedDataSource.value,
          props.userId,
          props.userType
        );
        
        const diagnosis = response.data;
        console.log('诊断结果:', diagnosis);
        
        if (diagnosis.error) {
          ElMessage.warning('诊断发现问题: ' + diagnosis.error);
          if (diagnosis.suggestion) {
            ElMessage.info('建议: ' + diagnosis.suggestion);
          }
        } else {
          console.log('诊断通过，数据库和表状态正常');
          console.log('表列数:', diagnosis.columnCount);
          console.log('表行数:', diagnosis.tableRows);
          console.log('是否为用户创建的数据库:', diagnosis.isUserCreatedDatabase);
          console.log('连接测试:', diagnosis.connectionTest);
        }
      } catch (error) {
        console.error('诊断失败:', error);
      }
    };

    // 重置导入器
    const resetImporter = () => {
      currentStep.value = 0;
      fileList.value = [];
      parsedData.value = [];
      csvColumns.value = [];
      columnMapping.value = [];
      showColumnMapping.value = false;
      validationResult.value = null;
      mappedData.value = [];
      importing.value = false;
      importProgress.value = 0;
      importResult.value = null;
      activeCollapse.value = [];
    };

    // 监听数据库选择的变化
    watch(selectedDataSource, (newDataSource) => {
      if (newDataSource) {
        // 重置相关状态
        selectedTable.value = '';
        tables.value = [];
        tableColumns.value = [];
        columnMapping.value = [];
        showColumnMapping.value = false;
        validationResult.value = null;
        
        // 重新加载表列表
        loadTables();
      }
    });

    onMounted(() => {
      loadDataSources();
    });

    return {
      currentStep,
      fileList,
      selectedDataSource,
      selectedTable,
      encoding,
      delimiter,
      hasHeader,
      parsedData,
      csvColumns,
      tableColumns,
      columnMapping,
      showColumnMapping,
      validationResult,
      mappedData,
      importMode,
      batchSize,
      importing,
      importProgress,
      importStatus,
      importStatusText,
      importResult,
      activeCollapse,
      dataSources,
      tables,
      previewData,
      loadDataSources,
      loadTables,
      loadTableColumns,
      handleFileChange,
      parseFile,
      validateAndProceed,
      startImport,
      resetImporter
    };
  }
};
</script>

<style scoped>
.csv-importer {
  max-width: 1200px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.upload-demo {
  margin-bottom: 20px;
}

.el-upload__tip {
  color: #999;
}

.el-steps {
  margin-bottom: 30px;
}

.el-form-item {
  margin-bottom: 15px;
}

.el-table {
  margin-bottom: 20px;
}

.el-progress {
  margin-bottom: 10px;
}

.el-alert {
  margin-bottom: 15px;
}

.el-collapse {
  margin-top: 10px;
}
</style> 