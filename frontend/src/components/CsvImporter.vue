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
            <el-form-item label="导入方式">
              <el-radio-group v-model="importMethod" @change="onImportMethodChange">
                <el-radio value="existing">导入到现有表</el-radio>
                <el-radio value="auto-create">自动建表导入</el-radio>
              </el-radio-group>
              <div style="color: #999; font-size: 12px; margin-top: 5px;">
                自动建表模式将根据CSV文件字段自动创建表，表名为文件名
              </div>
            </el-form-item>

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
              <el-col :span="12" v-if="importMethod === 'existing'">
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
              <el-col :span="12" v-if="importMethod === 'auto-create'">
                <el-form-item label="新表名">
                  <el-input 
                    v-model="autoTableName" 
                    placeholder="将使用CSV文件名作为表名"
                    @input="onTableNameChange"
                  />
                  <div style="color: #999; font-size: 12px; margin-top: 5px;">
                    系统将根据您输入的表名创建新表
                  </div>
                  <div v-if="tableExistsWarning" style="color: #e6a23c; font-size: 12px; margin-top: 5px;">
                    <el-icon><Warning /></el-icon>
                    表名 "{{ autoTableName }}" 在数据库 "{{ selectedDataSource }}" 中已存在，请修改表名或选择其他数据库
                  </div>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="编码格式">
                  <el-select v-model="encoding" placeholder="选择编码格式">
                    <el-option label="UTF-8 (现代标准)" value="utf-8" />
                    <el-option label="GBK (ANSI中文)" value="gbk" />
                    <el-option label="GB18030 (中文扩展)" value="gb18030" />
                    <el-option label="ASCII (纯英文)" value="ascii" />
                  </el-select>
                  <div style="color: #999; font-size: 12px; margin-top: 5px;">
                    <div>如出现乱码，请尝试不同的编码格式</div>
                    <div style="margin-top: 3px;">
                      <strong>推荐选择：</strong>
                      <span style="color: #409eff;">UTF-8</span>（现代标准） | 
                      <span style="color: #67c23a;">GBK</span>（记事本ANSI） |
                      <span style="color: #e6a23c;">GB18030</span>（中文扩展）
                    </div>
                  </div>
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
              :disabled="!selectedDataSource || (importMethod === 'existing' && !selectedTable) || (importMethod === 'auto-create' && tableExistsWarning)"
            >
              解析文件
            </el-button>
            <el-button 
              type="info" 
              @click="autoDetectEncoding" 
              :disabled="!fileList.length"
            >
              自动检测编码
            </el-button>
            
            <!-- ANSI编码特别提示 -->
            <div v-if="encoding === 'gbk'" style="margin-top: 10px; padding: 10px; background-color: #f0f9ff; border: 1px solid #409eff; border-radius: 4px;">
              <div style="color: #409eff; font-weight: bold; margin-bottom: 5px;">
                <el-icon><InfoFilled /></el-icon>
                GBK编码说明
              </div>
              <div style="color: #666; font-size: 12px;">
                • GBK编码对应Windows记事本的"ANSI"编码<br>
                • 适用于包含中文的Excel导出文件<br>
                • 如果UTF-8显示乱码，请选择此编码
              </div>
            </div>
          </div>
        </div>

        <!-- 步骤2：数据预览 -->
        <div v-if="currentStep === 1">
          <div style="margin-bottom: 20px;">
            <el-alert
              :title="importMethod === 'auto-create' ? '数据预览（自动建表模式）' : '数据预览'"
              :description="`解析到 ${parsedData.length} 条记录，显示前 ${Math.min(10, parsedData.length)} 条`"
              type="info"
              show-icon
            />
          </div>

          <!-- 自动建表模式的表结构预览 -->
          <div v-if="importMethod === 'auto-create'" style="margin-bottom: 20px;">
            <h4>自动建表信息</h4>
            <el-descriptions border :column="2">
              <el-descriptions-item label="表名">{{ autoTableName }}</el-descriptions-item>
              <el-descriptions-item label="字段数量">{{ csvColumns.length }}</el-descriptions-item>
            </el-descriptions>
            
            <h4 style="margin-top: 15px;">字段信息预览</h4>
            <div style="margin-bottom: 10px;">
              <el-alert
                title="字段配置说明"
                description="请选择一个字段作为主键，主键用于唯一标识记录和重复检测。如果不选择主键，系统将使用全行比较进行重复检测。您可以根据实际需要修改系统推断的数据类型。"
                type="info"
                show-icon
                :closable="false"
              />
            </div>
            <el-table :data="csvColumnsWithTypes" style="width: 100%; margin-bottom: 20px;">
              <el-table-column label="主键" width="80" align="center">
                <template #default="scope">
                  <el-checkbox 
                    v-model="scope.row.isPrimaryKey"
                    @change="updatePrimaryKeySelection"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="columnName" label="字段名" width="200">
                <template #default="scope">
                  <span>{{ scope.row.columnName }}</span>
                  <el-tag v-if="scope.row.isPrimaryKey" type="danger" size="small" style="margin-left: 8px;">主键</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="数据类型" width="300">
                <template #default="scope">
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <el-select 
                      v-model="scope.row.dataType" 
                      placeholder="选择数据类型"
                      style="width: 120px"
                      @change="(value) => onDataTypeChange(scope.row, value)"
                    >
                      <el-option
                        v-for="type in supportedDataTypes"
                        :key="type.name"
                        :label="type.name"
                        :value="type.name"
                      />
                    </el-select>
                    <el-input
                      v-if="scope.row.needsLength"
                      v-model="scope.row.length"
                      placeholder="长度"
                      style="width: 80px"
                      @input="(value) => onLengthChange(scope.row, value)"
                    />
                    <el-input
                      v-if="scope.row.needsDecimals"
                      v-model="scope.row.decimals"
                      placeholder="小数位"
                      style="width: 80px"
                      @input="(value) => onDecimalsChange(scope.row, value)"
                    />
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="sample" label="示例数据" />
            </el-table>
            
            <div style="margin-bottom: 15px;">
              <el-checkbox v-model="noPrimaryKey" @change="handleNoPrimaryKeyChange">
                不设置主键（使用全行比较进行重复检测）
              </el-checkbox>
            </div>
          </div>

          <!-- 列映射（仅现有表模式） -->
          <div v-if="showColumnMapping && importMethod === 'existing'" style="margin-bottom: 20px;">
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
              :disabled="importMethod === 'existing' && (!columnMapping.length || !columnMapping.some(mapping => mapping.dbColumn))"
            >
              {{ importMethod === 'auto-create' ? '继续' : '验证并继续' }}
            </el-button>
          </div>
        </div>

        <!-- 步骤3：数据导入 -->
        <div v-if="currentStep === 2">
          <div style="margin-bottom: 20px;">
            <el-alert
              title="导入设置"
              :description="importMethod === 'auto-create' 
                ? `即将创建表 ${autoTableName} 并导入 ${mappedData.length} 条记录` 
                : `即将导入 ${mappedData.length} 条记录到表 ${selectedTable}`"
              type="info"
              show-icon
            />
            
            <!-- 自动建表模式的特别提示 -->
            <div v-if="importMethod === 'auto-create'" style="margin-top: 10px;">
              <el-alert
                v-if="selectedPrimaryKeys.length > 0"
                :title="`已选择复合主键：${selectedPrimaryKeys.join(', ')}`"
                description="追加模式将基于此复合主键进行重复检测"
                type="success"
                show-icon
                :closable="false"
              />
              <el-alert
                v-else-if="noPrimaryKey"
                title="未设置主键"
                description="追加模式将使用全行比较进行重复检测"
                type="warning"
                show-icon
                :closable="false"
              />
              <el-alert
                v-else
                title="请选择主键"
                description="建议为表选择一个主键，以便更高效地进行重复检测"
                type="info"
                show-icon
                :closable="false"
              />
            </div>
          </div>

          <el-form label-width="120px">
            <el-form-item label="导入策略">
                          <el-radio-group v-model="importStrategy">
              <el-radio value="append">追加模式（检测重复数据，只导入不同的数据）</el-radio>
              <el-radio value="overwrite">覆盖模式（清空表后重新导入所有数据）</el-radio>
            </el-radio-group>
              <div style="color: #999; font-size: 12px; margin-top: 5px;">
                追加模式会检查数据重复性，覆盖模式会删除表中所有数据后重新导入
              </div>
            </el-form-item>

            <el-form-item label="导入模式">
                          <el-radio-group v-model="importMode">
              <el-radio value="normal">普通模式（快速，但可能部分失败）</el-radio>
              <el-radio value="transaction">事务模式（安全，全部成功或全部失败）</el-radio>
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
                <p v-if="importResult.result.skippedCount !== undefined">跳过重复: {{ importResult.result.skippedCount }}</p>
                <p v-if="importResult.result.deletedRows !== undefined">删除记录: {{ importResult.result.deletedRows }}</p>
                <p v-if="importResult.result.importStrategy">导入策略: {{ importResult.result.importStrategy === 'overwrite' ? '覆盖模式' : '追加模式' }}</p>
                <p v-if="importResult.result.tableCreated !== undefined">{{ importResult.result.tableCreated ? '已创建新表' : '使用现有表' }}</p>
                <p>耗时: {{ importResult.result.duration }}ms</p>
                <p v-if="importResult.result.message">{{ importResult.result.message }}</p>
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
              :disabled="importing || !mappedData.length || (importMethod === 'auto-create' && tableExistsWarning)"
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
import { UploadFilled, Warning, InfoFilled } from '@element-plus/icons-vue';
import Papa from 'papaparse';
import { databaseApi } from '../utils/api';

export default {
  name: 'CsvImporter',
  components: {
    UploadFilled,
    Warning,
    InfoFilled
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
    const autoTableName = ref('');
    const csvColumnsWithTypes = ref([]);
    const selectedPrimaryKeys = ref([]);
    const noPrimaryKey = ref(false);
    const importStrategy = ref('append');
    const importMethod = ref('existing'); // 'existing' 或 'auto-create'
    const batchSize = ref(5000);
    const importing = ref(false);
    const importProgress = ref(0);
    const importStatus = ref('');
    const importStatusText = ref('');
    const importResult = ref(null);
    const activeCollapse = ref([]);
    const tableExistsWarning = ref(false);
    const tableNameCheckTimeout = ref(null);

    const dataSources = ref([]);
    const tables = ref([]);
    const supportedDataTypes = ref([]);

    const previewData = computed(() => {
      return parsedData.value.slice(0, 10);
    });

    // 加载支持的数据类型
    const loadSupportedDataTypes = async () => {
      try {
        const response = await databaseApi.getSupportedDataTypes();
        // 保留完整的数据类型信息
        supportedDataTypes.value = response.data;
      } catch (error) {
        console.warn('加载支持的数据类型失败:', error.message);
        // 如果API失败，使用默认的数据类型列表
        supportedDataTypes.value = [
          { name: 'TINYINT', needsLength: false, needsDecimals: false },
          { name: 'SMALLINT', needsLength: false, needsDecimals: false },
          { name: 'MEDIUMINT', needsLength: false, needsDecimals: false },
          { name: 'INT', needsLength: false, needsDecimals: false },
          { name: 'BIGINT', needsLength: false, needsDecimals: false },
          { name: 'FLOAT', needsLength: true, needsDecimals: true },
          { name: 'DOUBLE', needsLength: true, needsDecimals: true },
          { name: 'DECIMAL', needsLength: true, needsDecimals: true },
          { name: 'CHAR', needsLength: true, needsDecimals: false },
          { name: 'VARCHAR', needsLength: true, needsDecimals: false },
          { name: 'TEXT', needsLength: false, needsDecimals: false },
          { name: 'MEDIUMTEXT', needsLength: false, needsDecimals: false },
          { name: 'LONGTEXT', needsLength: false, needsDecimals: false },
          { name: 'DATE', needsLength: false, needsDecimals: false },
          { name: 'DATETIME', needsLength: false, needsDecimals: false },
          { name: 'TIMESTAMP', needsLength: false, needsDecimals: false },
          { name: 'BOOLEAN', needsLength: false, needsDecimals: false }
        ];
      }
    };

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

    // 推断CSV列的数据类型
    const inferColumnTypes = () => {
      if (!csvColumns.value.length || !parsedData.value.length) return;
      
      csvColumnsWithTypes.value = csvColumns.value.map(column => {
        const sampleValues = parsedData.value.slice(0, 100).map(row => row[column]).filter(val => val !== null && val !== '');
        const inferredType = inferDataType(sampleValues);
        const sample = sampleValues.slice(0, 3).join(', ') || '空值';
        
        // 解析推断的类型，提取数据类型和长度
        const typeInfo = parseDataType(inferredType);
        
        return {
          columnName: column,
          inferredType: inferredType,
          dataType: typeInfo.dataType,
          length: typeInfo.length,
          decimals: typeInfo.decimals,
          needsLength: typeInfo.needsLength,
          needsDecimals: typeInfo.needsDecimals,
          sample: sample,
          isPrimaryKey: false // 默认不是主键，由用户选择
        };
      });
    };

    // 解析数据类型，提取类型名、长度和小数位
    const parseDataType = (typeString) => {
      const result = {
        dataType: typeString,
        length: null,
        decimals: null,
        needsLength: false,
        needsDecimals: false
      };
      
      // 匹配带长度和小数位的数据类型，如 DECIMAL(10,2)
      const match = typeString.match(/^(\w+)(?:\((\d+)(?:,(\d+))?\))?$/);
      if (match) {
        result.dataType = match[1];
        if (match[2]) {
          result.length = match[2];
          result.needsLength = true;
        }
        if (match[3]) {
          result.decimals = match[3];
          result.needsDecimals = true;
        }
      }
      
      // 根据数据类型设置是否需要长度和小数位
      const typeConfig = supportedDataTypes.value.find(t => t.name === result.dataType);
      if (typeConfig) {
        result.needsLength = typeConfig.needsLength;
        result.needsDecimals = typeConfig.needsDecimals;
      }
      
      return result;
    };

    // 数据类型推断逻辑
    const inferDataType = (values) => {
      if (!values.length) return 'VARCHAR(255)';
      
      let hasNumber = false;
      let hasDecimal = false;
      let hasDate = false;
      let maxLength = 0;
      let maxIntegerValue = 0;
      let minIntegerValue = 0;
      
      for (const value of values) {
        const str = String(value).trim();
        maxLength = Math.max(maxLength, str.length);
        
        // 检查是否为数字
        if (/^\d+$/.test(str)) {
          hasNumber = true;
          const numValue = parseInt(str);
          maxIntegerValue = Math.max(maxIntegerValue, numValue);
          minIntegerValue = Math.min(minIntegerValue, numValue);
        } else if (/^\d*\.\d+$/.test(str)) {
          hasDecimal = true;
        } else if (/^\d{4}-\d{2}-\d{2}/.test(str)) {
          hasDate = true;
        }
      }
      
      if (hasDate) return 'DATETIME';
      if (hasDecimal) return 'DECIMAL(10,2)';
      if (hasNumber) {
        // 更保守的整数类型推断策略
        // 如果数据量较少（少于1000条），使用更保守的推断
        const sampleSize = values.length;
        const isSmallSample = sampleSize < 1000;
        
        // 对于小样本，直接使用BIGINT以确保安全
        if (isSmallSample) {
          return 'BIGINT';
        }
        
        // 对于大样本，根据实际数值范围推断
        if (maxIntegerValue <= 127 && minIntegerValue >= -128) {
          return 'TINYINT';
        } else if (maxIntegerValue <= 32767 && minIntegerValue >= -32768) {
          return 'SMALLINT';
        } else if (maxIntegerValue <= 8388607 && minIntegerValue >= -8388608) {
          return 'MEDIUMINT';
        } else if (maxIntegerValue <= 2147483647 && minIntegerValue >= -2147483648) {
          return 'INT';
        } else {
          return 'BIGINT';
        }
      }
      
      // 根据实际文本长度推断VARCHAR长度
      if (maxLength <= 255) {
        // 为文本长度预留一些空间，但不少于50，不超过255
        const suggestedLength = Math.min(255, Math.max(50, maxLength + 20));
        return `VARCHAR(${suggestedLength})`;
      }
      return 'TEXT';
    };

    // 更新主键选择
    const updatePrimaryKeySelection = () => {
      // 收集所有被选中的主键列
      selectedPrimaryKeys.value = csvColumnsWithTypes.value
        .filter(col => col.isPrimaryKey)
        .map(col => col.columnName);
      
      // 如果有选择主键，取消"不设置主键"选项
      if (selectedPrimaryKeys.value.length > 0) {
        noPrimaryKey.value = false;
      }
    };

    // 处理"不设置主键"选项
    const handleNoPrimaryKeyChange = (value) => {
      if (value) {
        selectedPrimaryKeys.value = [];
        csvColumnsWithTypes.value.forEach(col => {
          col.isPrimaryKey = false;
        });
      }
    };

    // 处理文件选择
    const handleFileChange = (file) => {
      fileList.value = [file];
      // 自动设置表名（去掉文件扩展名）
      if (file && file.name) {
        autoTableName.value = file.name.replace(/\.[^/.]+$/, '').replace(/[^a-zA-Z0-9_]/g, '_');
      }
    };

    // 处理导入方式变化
    const onImportMethodChange = (method) => {
      if (method === 'auto-create') {
        // 切换到自动建表模式时，清空表相关数据
        selectedTable.value = '';
        tableColumns.value = [];
        columnMapping.value = [];
        showColumnMapping.value = false;
        validationResult.value = null;
      } else {
        // 切换到现有表模式时，重新加载表列表
        if (selectedDataSource.value) {
          loadTables();
        }
      }
    };

    // 解析CSV文件
    const parseFile = () => {
      if (!fileList.value.length) {
        ElMessage.error('请先选择文件');
        return;
      }

      const file = fileList.value[0].raw;
      
      // 使用FileReader根据指定编码读取文件
      const reader = new FileReader();
      
      reader.onload = (e) => {
        const content = e.target.result;
        
        Papa.parse(content, {
          header: hasHeader.value,
          delimiter: delimiter.value,
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
            
            if (importMethod.value === 'existing' && tableColumns.value.length > 0) {
              setupColumnMapping();
            } else if (importMethod.value === 'auto-create') {
              // 自动建表模式，推断字段类型
              inferColumnTypes();
              // 重置主键选择
              selectedPrimaryKeys.value = [];
              noPrimaryKey.value = false;
            }
            
            currentStep.value = 1;
          },
          error: (error) => {
            ElMessage.error('CSV解析失败: ' + error.message);
          }
        });
      };
      
      reader.onerror = (e) => {
        ElMessage.error('文件读取失败，请检查文件是否完整或尝试其他编码格式');
      };
      
      // 根据选择的编码格式读取文件
      try {
        if (encoding.value === 'gbk' || encoding.value === 'gb18030') {
          // 对于GBK/GB18030编码，需要特殊处理
          reader.readAsArrayBuffer(file);
          reader.onload = (e) => {
            try {
              // 使用TextDecoder解码中文编码
              const decoder = new TextDecoder(encoding.value);
              const content = decoder.decode(e.target.result);
              parseContent(content);
            } catch (decodeError) {
              ElMessage.error(`${encoding.value.toUpperCase()}编码解析失败，请尝试UTF-8编码: ` + decodeError.message);
              
              // 如果GBK失败，自动尝试GB18030
              if (encoding.value === 'gbk') {
                try {
                  const gb18030Decoder = new TextDecoder('gb18030');
                  const content = gb18030Decoder.decode(e.target.result);
                  parseContent(content);
                  ElMessage.success('已自动切换到GB18030编码解析');
                } catch (fallbackError) {
                  ElMessage.error('自动切换GB18030编码也失败，请手动选择UTF-8编码');
                }
              }
            }
          };
        } else {
          // UTF-8和ASCII编码
          reader.readAsText(file, encoding.value);
        }
      } catch (error) {
        ElMessage.error('不支持的编码格式: ' + encoding.value);
      }
      
      // 解析内容的通用方法
      function parseContent(content) {
        Papa.parse(content, {
          header: hasHeader.value,
          delimiter: delimiter.value,
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
            
            if (importMethod.value === 'existing' && tableColumns.value.length > 0) {
              setupColumnMapping();
            } else if (importMethod.value === 'auto-create') {
              // 自动建表模式，推断字段类型
              inferColumnTypes();
              // 重置主键选择
              selectedPrimaryKeys.value = [];
              noPrimaryKey.value = false;
            }
            
            currentStep.value = 1;
          },
          error: (error) => {
            ElMessage.error('CSV解析失败: ' + error.message);
          }
        });
      }
    };

    // 智能编码检测（支持中英文混合）
    const autoDetectEncoding = async () => {
      if (!fileList.value.length) {
        ElMessage.error('请先选择文件');
        return;
      }

      const file = fileList.value[0].raw;
      const reader = new FileReader();
      
      reader.onload = (e) => {
        const buffer = e.target.result;
        const uint8Array = new Uint8Array(buffer);
        
        // 检测结果
        let detectedEncoding = 'utf-8';
        let confidence = '低';
        let detectionDetails = [];
        
        // 1. 检测BOM标记
        if (uint8Array.length >= 3 && 
            uint8Array[0] === 0xEF && 
            uint8Array[1] === 0xBB && 
            uint8Array[2] === 0xBF) {
          detectedEncoding = 'utf-8';
          confidence = '高';
          detectionDetails.push('检测到UTF-8 BOM标记');
          ElMessage.success(`检测到UTF-8编码（带BOM），置信度：${confidence}`);
          encoding.value = detectedEncoding;
          return;
        }
        
        // 2. 多编码尝试解析（取样本进行测试）
        const sampleSize = Math.min(2000, uint8Array.length);
        const sample = uint8Array.slice(0, sampleSize);
        
        // ANSI检测：检查是否包含中文字节范围
        const hasChineseBytes = sample.some(byte => byte >= 0x80 && byte <= 0xFF);
        
        const encodingTests = [
          { name: 'gbk', decoder: new TextDecoder('gbk'), priority: hasChineseBytes ? 3 : 1 }, // ANSI优先
          { name: 'utf-8', decoder: new TextDecoder('utf-8', { fatal: true }), priority: 2 },
          { name: 'gb18030', decoder: new TextDecoder('gb18030'), priority: 1 }
        ];
        
        // 根据优先级排序
        encodingTests.sort((a, b) => b.priority - a.priority);
        
        let bestEncoding = null;
        let bestScore = -1;
        
        for (const test of encodingTests) {
          try {
            const content = test.decoder.decode(sample);
            let score = 0;
            
            // 评分标准
            const chineseChars = (content.match(/[\u4e00-\u9fff]/g) || []).length;
            const englishChars = (content.match(/[a-zA-Z]/g) || []).length;
            const numbers = (content.match(/[0-9]/g) || []).length;
            const validChars = chineseChars + englishChars + numbers;
            const totalChars = content.length;
            
            // 计算有效字符比例
            const validRatio = totalChars > 0 ? validChars / totalChars : 0;
            
            // 检查是否有乱码特征
            const hasGarbage = /[��\uFFFD\x00-\x08\x0B\x0C\x0E-\x1F]/.test(content);
            const hasValidStructure = /[,;\t\r\n]/.test(content); // CSV结构特征
            
            // 评分计算
            score = validRatio * 100;
            if (hasGarbage) score -= 50;
            if (!hasValidStructure) score -= 20;
            if (chineseChars > 0 && englishChars > 0) score += 10; // 中英混合加分
            
            // ANSI/GBK特殊加分
            if (test.name === 'gbk' && hasChineseBytes && chineseChars > 0) {
              score += 15; // GBK解析中文成功时加分
            }
            
            // UTF-8严格模式额外检查
            if (test.name === 'utf-8' && !hasGarbage && chineseChars > 0) {
              score += 5; // UTF-8无乱码且有中文时加分
            }
            
            detectionDetails.push(`${test.name.toUpperCase()}: 有效字符${(validRatio*100).toFixed(1)}%, 中文${chineseChars}个, 英文${englishChars}个, 评分${score.toFixed(1)}`);
            
            if (score > bestScore) {
              bestScore = score;
              bestEncoding = test.name;
              
              // 设置置信度
              if (score >= 80) confidence = '高';
              else if (score >= 60) confidence = '中';
              else confidence = '低';
            }
            
          } catch (error) {
            detectionDetails.push(`${test.name.toUpperCase()}: 解码失败`);
          }
        }
        
        detectedEncoding = bestEncoding || 'utf-8';
        
        // 显示详细检测结果
        const message = `
检测结果：${detectedEncoding.toUpperCase()}编码
置信度：${confidence}
评分：${bestScore.toFixed(1)}

检测详情：
${detectionDetails.join('\n')}

建议：
• 记事本显示ANSI编码 → 选择GBK编码
• 中英文混合文件推荐UTF-8编码
• Excel导出的CSV通常是GBK编码
• 如果UTF-8乱码，大概率是GBK编码
        `.trim();
        
        ElMessageBox.alert(message, '编码检测结果', {
          confirmButtonText: '确定',
          type: 'info'
        });
        
        encoding.value = detectedEncoding;
      };
      
      reader.onerror = () => {
        ElMessage.error('文件读取失败，无法检测编码');
      };
      
      reader.readAsArrayBuffer(file);
    };

    // 验证数据并继续
    const validateAndProceed = async () => {
      if (importMethod.value === 'auto-create') {
        // 自动建表模式，直接使用原始数据
        mappedData.value = parsedData.value;
        currentStep.value = 2;
        return;
      }
      
      // 现有表模式，进行数据映射和验证
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
        const strategyText = importStrategy.value === 'overwrite' 
          ? '（覆盖模式：将清空表中所有数据后重新导入）' 
          : '（追加模式：检测重复数据，只导入不同的数据）';
        
        const tableName = importMethod.value === 'auto-create' ? autoTableName.value : selectedTable.value;
        const actionText = importMethod.value === 'auto-create' 
          ? `创建表 ${tableName} 并导入 ${mappedData.value.length} 条记录` 
          : `导入 ${mappedData.value.length} 条记录到表 ${tableName}`;
        
        await ElMessageBox.confirm(
          `确定要${actionText}吗？${importMethod.value === 'existing' ? strategyText : ''}`,
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
        importStatusText.value = importMethod.value === 'auto-create' ? '正在创建表并导入数据...' : '正在导入数据...';

        let response;
        if (importMethod.value === 'auto-create') {
          // 调试信息：检查发送给后端的数据
          console.log('发送给后端的csvColumns:', csvColumnsWithTypes.value)
          const primaryKeyColumns = csvColumnsWithTypes.value.filter(col => col.isPrimaryKey)
          console.log('选中的主键列:', primaryKeyColumns)
          console.log('主键列数量:', primaryKeyColumns.length)
          
          // 自动建表导入
          response = await databaseApi.autoCreateTableAndImport({
            dataSource: selectedDataSource.value,
            tableName: autoTableName.value,
            csvData: mappedData.value,
            csvColumns: csvColumnsWithTypes.value,
            useTransaction: importMode.value === 'transaction',
            importStrategy: importStrategy.value,
            userId: props.userId,
            userType: props.userType
          });
        } else {
          // 现有表导入
          response = await databaseApi.batchInsertTableData(
            selectedTable.value,
            {
              dataSource: selectedDataSource.value,
              dataList: mappedData.value,
              useTransaction: importMode.value === 'transaction',
              importStrategy: importStrategy.value,
              userId: props.userId,
              userType: props.userType
            }
          );
        }

        importResult.value = response.data;
        importProgress.value = 100;
        importStatus.value = importResult.value.success ? 'success' : 'exception';
        importStatusText.value = importResult.value.success ? '导入完成' : '导入失败';

                  if (importResult.value.success) {
            const successMessage = importMethod.value === 'auto-create' 
              ? (importResult.value.result.tableCreated ? '创建表并导入数据成功' : '导入数据成功')
              : '数据导入成功';
            ElMessage.success(successMessage);
            
            // 触发import-complete事件，通知父组件
            const tableName = importMethod.value === 'auto-create' ? autoTableName.value : selectedTable.value;
            emit('import-complete', {
              ...importResult.value.result,
              dataSource: selectedDataSource.value,
              tableName: tableName
            });
        } else {
          ElMessage.error('数据导入失败');
          
          // 如果导入失败，自动进行诊断（仅对现有表）
          if (importResult.value.result && importMethod.value === 'existing') {
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
          
          // 导入异常时也进行诊断（仅对现有表）
          if (importMethod.value === 'existing') {
            await performDiagnosis();
          }
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
      importStrategy.value = 'append';
      importMethod.value = 'existing';
      autoTableName.value = '';
      csvColumnsWithTypes.value = [];
      selectedPrimaryKeys.value = [];
      noPrimaryKey.value = false;
      // 清除数据类型相关的临时变量
      if (csvColumnsWithTypes.value.length > 0) {
        csvColumnsWithTypes.value.forEach(col => {
          col.dataType = null;
          col.length = null;
          col.decimals = null;
          col.needsLength = false;
          col.needsDecimals = false;
        });
      }
      importing.value = false;
      importProgress.value = 0;
      importResult.value = null;
      activeCollapse.value = [];
      tableExistsWarning.value = false;
      if (tableNameCheckTimeout.value) {
        clearTimeout(tableNameCheckTimeout.value);
        tableNameCheckTimeout.value = null;
      }
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
        
        // 如果是自动建表模式且有表名，检查表是否存在
        if (importMethod.value === 'auto-create' && autoTableName.value) {
          checkTableExists();
        }
      }
    });

    // 监听表名变化（自动建表模式）
    watch(autoTableName, (newTableName) => {
      if (importMethod.value === 'auto-create' && newTableName && selectedDataSource.value) {
        checkTableExists();
      }
    });

    // 检查表是否存在
    const checkTableExists = async () => {
      if (!selectedDataSource.value || !autoTableName.value) {
        tableExistsWarning.value = false;
        return;
      }
      
      try {
        const response = await databaseApi.checkTableExists(
          selectedDataSource.value,
          selectedDataSource.value, // 对于用户创建的数据库，数据库名就是数据源名
          autoTableName.value
        );
        
        tableExistsWarning.value = response.data.exists;
        
        if (response.data.exists) {
          ElMessage.warning(`表名 "${autoTableName.value}" 在数据库 "${selectedDataSource.value}" 中已存在，请修改表名或选择其他数据库`);
        }
      } catch (error) {
        console.warn('检查表是否存在失败:', error.message);
        tableExistsWarning.value = false;
      }
    };

    // 处理表名变化
    const onTableNameChange = () => {
      // 使用防抖，避免频繁请求
      clearTimeout(tableNameCheckTimeout.value);
      tableNameCheckTimeout.value = setTimeout(() => {
        if (importMethod.value === 'auto-create' && autoTableName.value && selectedDataSource.value) {
          checkTableExists();
        }
      }, 500);
    };

    // 处理数据类型变化
    const onDataTypeChange = (column, newType) => {
      column.dataType = newType;
      
      // 根据新的数据类型更新needsLength和needsDecimals
      const typeConfig = supportedDataTypes.value.find(t => t.name === newType);
      if (typeConfig) {
        column.needsLength = typeConfig.needsLength;
        column.needsDecimals = typeConfig.needsDecimals;
        
        // 如果不需要长度或小数位，清空相关值
        if (!typeConfig.needsLength) {
          column.length = null;
        }
        if (!typeConfig.needsDecimals) {
          column.decimals = null;
        }
      }
      
      // 更新inferredType
      updateInferredType(column);
      
      ElMessage.success(`已将字段 "${column.columnName}" 的数据类型修改为 "${newType}"`);
    };

    // 处理长度变化
    const onLengthChange = (column, newLength) => {
      column.length = newLength;
      updateInferredType(column);
    };

    // 处理小数位变化
    const onDecimalsChange = (column, newDecimals) => {
      column.decimals = newDecimals;
      updateInferredType(column);
    };

    // 更新推断类型字符串
    const updateInferredType = (column) => {
      let typeString = column.dataType;
      if (column.needsLength && column.length) {
        typeString += `(${column.length}`;
        if (column.needsDecimals && column.decimals) {
          typeString += `,${column.decimals}`;
        }
        typeString += ')';
      }
      column.inferredType = typeString;
    };

    onMounted(() => {
      loadDataSources();
      loadSupportedDataTypes();
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
      importStrategy,
      importMethod,
      autoTableName,
      csvColumnsWithTypes,
      selectedPrimaryKeys,
      noPrimaryKey,
      batchSize,
      importing,
      importProgress,
      importStatus,
      importStatusText,
      importResult,
      activeCollapse,
      tableExistsWarning,
      dataSources,
      tables,
      supportedDataTypes,
      previewData,
      loadDataSources,
      loadTables,
      loadTableColumns,
      handleFileChange,
      onImportMethodChange,
      parseFile,
      autoDetectEncoding,
      validateAndProceed,
      startImport,
      resetImporter,
      inferColumnTypes,
      updatePrimaryKeySelection,
      handleNoPrimaryKeyChange,
      checkTableExists,
      onTableNameChange,
      onDataTypeChange,
      onLengthChange,
      onDecimalsChange
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