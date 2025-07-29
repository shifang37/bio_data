<template>
  <div class="database-view">
    <div class="database-layout">
      <!-- 左侧数据库导航 -->
      <div class="database-sidebar">
                 <el-card class="sidebar-card" shadow="never">
      <template #header>
             <div class="sidebar-header">
               <div class="sidebar-title">
                 <el-icon><Coin /></el-icon>
                 <span>数据库</span>
               </div>
               <el-button 
                 type="primary" 
                 size="small" 
                 @click="showCreateDatabaseDialog"
                 v-if="canCreateDatabase"
            >
                 <el-icon><Plus /></el-icon>
                 新建
               </el-button>
             </div>
           </template>
          
          <div class="database-list">
            <div
                v-for="db in availableDatabases"
                :key="db.name"
              class="database-item"
              :class="{ active: selectedDatabase === db.name }"
            >
              <div 
                class="database-content"
                @click="selectDatabase(db.name)"
              >
                <div class="database-icon">
                  <el-icon v-if="selectedDatabase === db.name">
                    <FolderOpened />
                  </el-icon>
                  <el-icon v-else>
                    <Folder />
                  </el-icon>
                </div>
                <div class="database-info">
                  <div class="database-name">{{ db.displayName }}</div>
                  <div class="database-desc">{{ db.description }}</div>
                </div>
              </div>
              
              <!-- 删除按钮（仅对用户创建的数据库显示，且仅管理员可见） -->
              <div 
                v-if="canDeleteDatabase(db) && canCreateDatabase" 
                class="database-actions"
                @click.stop
              >
                <el-button 
                  type="danger" 
                  size="small" 
                  :icon="Delete"
                  circle
                  @click="showDeleteDatabaseDialog(db)"
                  title="删除数据库"
                />
              </div>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 右侧内容区域 -->
      <div class="database-content">
        <div class="database-content-layout">
          <!-- 数据库信息区域 -->
          <el-card class="database-info-card" v-if="selectedDatabase">
            <template #header>
              <div class="card-header">
                <div class="database-title">
                  <el-icon><Coin /></el-icon>
                  <span>{{ getSelectedDatabaseInfo()?.displayName || selectedDatabase }}</span>
                </div>
                <el-button type="primary" @click="loadTables" :loading="loadingTables">
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
              </div>
            </template>
            
            <!-- 权限提示 -->
            <div v-if="permissionMessage" class="permission-alert">
              <el-alert
                :title="permissionMessage"
                type="warning"
                show-icon
                :closable="false"
                style="margin-bottom: 15px;"
              />
            </div>
            
            <!-- 数据库信息展示 -->
            <div v-if="canAccessCurrentDatabase">
              <el-row :gutter="20" v-if="databaseInfo">
                <el-col :span="8">
                  <el-statistic title="当前数据库" :value="databaseInfo.name || selectedDatabase" />
                </el-col>
                <el-col :span="8">
                  <el-statistic title="数据表总数" :value="databaseInfo.tableCount || 0" />
                </el-col>
                <el-col :span="8">
                  <el-statistic title="数据库大小" :value="databaseInfo.sizeDisplay || 'N/A'" />
                </el-col>
              </el-row>
              
              <!-- 数据库信息加载中 -->
              <el-row :gutter="20" v-else>
                <el-col :span="8">
                  <el-statistic title="当前数据库" :value="selectedDatabase" />
                </el-col>
                <el-col :span="8">
                  <el-statistic title="数据表总数" :value="'加载中...'" />
                </el-col>
                <el-col :span="8">
                  <el-statistic title="数据库大小" :value="'加载中...'" />
                </el-col>
              </el-row>
            </div>
          </el-card>

          <!-- 数据表列表 -->
          <el-card class="tables-card" v-if="selectedDatabase">
            <template #header>
              <div class="card-header">
                <div class="card-title">
                  <span>数据表列表</span>
                  <el-button 
                    v-if="canAccessCurrentDatabase && canModifyCurrentDatabase && isUserDatabase(selectedDatabase)"
                    type="success" 
                    size="small" 
                    @click="showCreateTableDialog"
                    style="margin-left: 15px;"
                  >
                    <el-icon><Plus /></el-icon>
                    新建表
                  </el-button>
                </div>
                <div v-if="canAccessCurrentDatabase" style="display: flex; align-items: center; gap: 10px;">
                  <!-- 字段值搜索框 -->
                  <el-input
                    v-model="fieldSearchQuery"
                    placeholder="按字段值搜索..."
                    style="width: 200px;"
                    clearable
                    @keyup.enter="searchTablesByFieldValue"
                  >
                    <template #prefix>
                      <el-icon><Search /></el-icon>
                    </template>
                  </el-input>
                  
                  <!-- 搜索模式选择器 -->
                  <el-select 
                    v-model="searchMode" 
                    style="width: 120px;" 
                    size="small"
                    title="选择搜索模式：智能模式会根据搜索内容自动判断，文本模式仅搜索文本字段（适合蛋白质名称），数字模式仅搜索数字字段"
                  >
                    <el-option label="智能模式" value="auto" />
                    <el-option label="仅文本字段" value="text_only" />
                    <el-option label="仅数字字段" value="numeric_only" />
                    <el-option label="全部字段" value="all" />
                  </el-select>
                  
                  <el-button 
                    type="primary" 
                    @click="searchTablesByFieldValue" 
                    :loading="fieldSearching"
                    size="small"
                    title="搜索结果可以最小化保留，切换数据库不会影响"
                  >
                    字段搜索
                  </el-button>
                  
                  <!-- 表名搜索框 -->
                  <el-input
                    v-model="searchQuery"
                    placeholder="搜索表名..."
                    style="width: 200px;"
                    clearable
                  >
                    <template #prefix>
                      <el-icon><Search /></el-icon>
                    </template>
                  </el-input>
                </div>
              </div>
            </template>
            
            <!-- 字段搜索错误信息 -->
                      <div v-if="fieldSearchError" style="margin-bottom: 20px;">
            <el-alert 
              :title="fieldSearchError" 
              type="error" 
              :closable="false"
              show-icon
            />
          </div>

          <!-- 搜索功能提示 -->
          <div v-if="searchDialogs.length > 0" style="margin-bottom: 15px;">
            <el-alert
              title="💡 提示"
              type="info"
              :closable="false"
              show-icon
            >
              <template #default>
                <p style="margin: 0;">当前保留了 {{ searchDialogs.length }} 个搜索结果弹窗。切换数据库不会影响这些搜索结果，它们会一直保留直到您手动关闭。</p>
              </template>
            </el-alert>
          </div>

            <!-- 有权限访问时显示表格 -->
            <div v-if="canAccessCurrentDatabase" class="table-container">
              <el-table 
                :data="filteredTableList" 
                style="width: 100%" 
                :max-height="getTableMaxHeight()"
                @row-click="selectTable"
                v-loading="loadingTables"
                element-loading-text="加载表数据中..."
              >
                <el-table-column prop="TABLE_NAME" label="表名" width="250" sortable />
                <el-table-column prop="TABLE_ROWS" label="行数" width="120" sortable>
                  <template #default="scope">
                    {{ scope.row.TABLE_ROWS?.toLocaleString() || 'N/A' }}
                  </template>
                </el-table-column>
                <el-table-column prop="DATA_LENGTH" label="数据大小" width="120" sortable>
                  <template #default="scope">
                    {{ formatBytes(scope.row.DATA_LENGTH) }}
                  </template>
                </el-table-column>
                <el-table-column prop="CREATE_TIME" label="创建时间" width="180" sortable>
                  <template #default="scope">
                    {{ formatDate(scope.row.CREATE_TIME) }}
                  </template>
                </el-table-column>
                <el-table-column prop="TABLE_COMMENT" label="注释" show-overflow-tooltip />
                <el-table-column label="操作" width="280" fixed="right">
                  <template #default="scope">
                    <el-button size="small" @click="viewTableData(scope.row.TABLE_NAME)">
                      查看数据
                    </el-button>
                    <el-button size="small" @click="viewTableStructure(scope.row.TABLE_NAME)">
                      表结构
                    </el-button>
                    <el-button 
                      v-if="canModifyCurrentDatabase && isUserDatabase(selectedDatabase)"
                      size="small" 
                      type="danger"
                      @click="confirmDeleteTable(scope.row.TABLE_NAME)"
                    >
                      删除表
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              
              <!-- 空状态 -->
              <el-empty v-if="!loadingTables && filteredTableList.length === 0" description="没有找到相关表" />
            </div>
            
            <!-- 无权限访问时显示提示 -->
            <div v-else>
              <el-empty description="您没有权限访问此数据库">
                <template #image>
                  <div style="font-size: 60px; color: #f56c6c;">🔒</div>
                </template>
              </el-empty>
            </div>
          </el-card>

          <!-- 请选择数据库提示 -->
          <el-card v-else class="welcome-card">
            <el-empty description="请从左侧选择一个数据库来查看其表结构">
              <template #image>
                <div style="font-size: 60px; color: #409eff;">
                  <el-icon><Coin /></el-icon>
                </div>
              </template>
            </el-empty>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 表结构对话框 -->
    <el-dialog v-model="structureDialogVisible" :title="`表结构 - ${selectedTable}`" width="70%">
      <el-table :data="tableColumns" style="width: 100%">
        <el-table-column prop="COLUMN_NAME" label="列名" width="150" />
        <el-table-column prop="DATA_TYPE" label="数据类型" width="120" />
        <el-table-column prop="IS_NULLABLE" label="是否可空" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.IS_NULLABLE === 'YES' ? 'success' : 'danger'">
              {{ scope.row.IS_NULLABLE }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="COLUMN_DEFAULT" label="默认值" width="120" />
        <el-table-column prop="COLUMN_KEY" label="键类型" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.COLUMN_KEY === 'PRI'" type="warning">主键</el-tag>
            <el-tag v-else-if="scope.row.COLUMN_KEY === 'UNI'" type="info">唯一</el-tag>
            <el-tag v-else-if="scope.row.COLUMN_KEY === 'MUL'" type="success">索引</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="EXTRA" label="额外信息" width="120" />
        <el-table-column prop="COLUMN_COMMENT" label="注释" />
      </el-table>
    </el-dialog>

    <!-- 表数据对话框 -->
    <el-dialog 
      v-model="dataDialogVisible" 
      :title="fieldSearchResult.searchValue ? `表数据 - ${selectedTable} (搜索值: ${fieldSearchResult.searchValue})` : `表数据 - ${selectedTable}`" 
      width="90%"
    >
      <div style="margin-bottom: 15px; display: flex; justify-content: space-between; align-items: center;">
        <div>
          <el-text type="info">
            每页显示：
          </el-text>
          <el-select v-model="pagination.pageSize" @change="changePageSize" style="width: 80px; margin: 0 10px;">
            <el-option label="10" :value="10" />
            <el-option label="20" :value="20" />
            <el-option label="50" :value="50" />
            <el-option label="100" :value="100" />
          </el-select>
          <el-text type="info">
            条，共 {{ pagination.totalCount }} 条记录
          </el-text>
        </div>
        <div>
          <el-button type="primary" @click="loadTableDataWithPagination" :loading="tableDataLoading">
            刷新数据
          </el-button>
          <el-button 
            v-if="canModifyCurrentDatabase"
            type="success" 
            @click="showAddDataDialog" 
            style="margin-left: 10px;"
          >
            新增数据
          </el-button>
        </div>
      </div>
      
              <el-table 
        :data="tableData" 
        style="width: 100%" 
        v-loading="tableDataLoading"
        element-loading-text="加载中..."
      >
        <el-table-column 
          v-for="column in Object.keys(tableData[0] || {})" 
          :key="column"
          :prop="column" 
          :label="column"
          :width="getColumnWidth(column)"
          show-overflow-tooltip
        >
          <template #default="scope">
            <span 
              v-if="fieldSearchResult.searchValue && cellContainsSearchValue(scope.row[column], fieldSearchResult.searchValue)" 
              style="background-color: #fff2e8; padding: 2px 4px; border-radius: 3px; color: #e6a23c; font-weight: bold;"
            >
              {{ scope.row[column] }}
            </span>
            <span v-else>{{ scope.row[column] }}</span>
          </template>
        </el-table-column>
        <el-table-column 
          v-if="tableData.length > 0 && canModifyCurrentDatabase" 
          label="操作" 
          width="170" 
          fixed="right"
          align="center"
        >
          <template #default="scope">
            <div class="table-actions">
              <el-button 
                size="small" 
                type="primary" 
                @click="editRow(scope.row)"
                :icon="Edit"
                class="action-btn"
              >
                编辑
              </el-button>
              <el-button 
                size="small" 
                type="danger" 
                @click="confirmDeleteRow(scope.row)"
                :icon="Delete"
                class="action-btn"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页组件 -->
      <div style="margin-top: 20px; display: flex; justify-content: center;">
        <el-pagination
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :small="false"
          :disabled="tableDataLoading"
          :background="true"
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.totalCount"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-dialog>

    <!-- 新增数据对话框 -->
    <el-dialog v-model="addDataDialogVisible" :title="`新增数据 - ${selectedTable}`" width="70%">
      <!-- 主键值提示 -->
      <el-alert 
        v-if="hasPrimaryKey"
        title="提示：此表有主键字段，请确保输入的值是唯一的"
        type="warning"
        :closable="false"
        style="margin-bottom: 20px;"
      />
      
      <el-form :model="newRowData" label-width="120px">
        <el-form-item 
          v-for="column in tableColumns" 
          :key="column.COLUMN_NAME"
          :label="column.COLUMN_NAME"
          :required="column.IS_NULLABLE === 'NO' && !column.EXTRA.includes('auto_increment')"
        >
          <div style="display: flex; align-items: center; gap: 10px;">
            <el-input 
              v-model="newRowData[column.COLUMN_NAME]"
              :placeholder="getColumnPlaceholder(column)"
              :disabled="column.EXTRA.includes('auto_increment')"
              style="flex: 1;"
            />
            <el-tag size="small" :type="getColumnTagType(column)">
              {{ column.DATA_TYPE }}
            </el-tag>
          </div>
          <div style="font-size: 12px; color: #666; margin-top: 5px;">
            {{ getColumnDescription(column) }}
          </div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="addDataDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitNewData" :loading="submitting">
          提交
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑数据对话框 -->
    <el-dialog v-model="editDataDialogVisible" :title="`编辑数据 - ${selectedTable}`" width="70%">
      <!-- 主键提示 -->
      <el-alert 
        title="注意：主键字段不能修改"
        type="info"
        :closable="false"
        style="margin-bottom: 20px;"
      />
      
      <el-form :model="editRowData" label-width="120px">
        <el-form-item 
          v-for="column in tableColumns" 
          :key="column.COLUMN_NAME"
          :label="column.COLUMN_NAME"
        >
          <div style="display: flex; align-items: center; gap: 10px;">
            <el-input 
              v-model="editRowData[column.COLUMN_NAME]"
              :placeholder="getColumnPlaceholder(column)"
              :disabled="column.COLUMN_KEY === 'PRI' || column.EXTRA.includes('auto_increment')"
              style="flex: 1;"
            />
            <el-tag size="small" :type="getColumnTagType(column)">
              {{ column.DATA_TYPE }}
            </el-tag>
          </div>
          <div style="font-size: 12px; color: #666; margin-top: 5px;">
            {{ getColumnDescription(column) }}
          </div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="editDataDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEditData" :loading="submitting">
          保存修改
        </el-button>
      </template>
    </el-dialog>

    <!-- 新建数据库对话框 -->
    <el-dialog v-model="createDatabaseDialogVisible" title="新建数据库" width="600px">
      <el-form :model="newDatabaseForm" label-width="120px" :rules="databaseRules" ref="databaseFormRef">
        <el-form-item label="数据库名称" prop="databaseName">
          <el-input 
            v-model="newDatabaseForm.databaseName"
            placeholder="请输入数据库名称（仅支持字母、数字、下划线）"
            maxlength="64"
            show-word-limit
          />
          <div style="font-size: 12px; color: #666; margin-top: 5px;">
            数据库名称只能包含字母、数字和下划线，长度不超过64个字符
  </div>
        </el-form-item>
        
        <el-form-item label="字符集" prop="charset">
          <el-select v-model="newDatabaseForm.charset" placeholder="选择字符集" style="width: 100%;">
            <el-option label="utf8（推荐）" value="utf8" />
            <el-option label="utf8mb4" value="utf8mb4" />
            <el-option label="latin1" value="latin1" />
            <el-option label="gbk" value="gbk" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="排序规则" prop="collation">
          <el-select v-model="newDatabaseForm.collation" placeholder="选择排序规则" style="width: 100%;">
            <el-option label="utf8_general_ci（推荐）" value="utf8_general_ci" />
            <el-option label="utf8_unicode_ci" value="utf8_unicode_ci" />
            <el-option label="utf8mb4_unicode_ci" value="utf8mb4_unicode_ci" />
            <el-option label="utf8mb4_general_ci" value="utf8mb4_general_ci" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="数据库描述">
          <el-input 
            v-model="newDatabaseForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入数据库的用途描述（可选）"
            maxlength="200"
            show-word-limit
          />
          <div style="font-size: 12px; color: #666; margin-top: 5px;">
            描述这个数据库的用途，便于后续管理
          </div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="createDatabaseDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createDatabase" :loading="creatingDatabase">
          创建数据库
        </el-button>
      </template>
    </el-dialog>

    <!-- 删除数据库确认对话框 -->
    <el-dialog v-model="deleteDatabaseDialogVisible" title="删除数据库" width="500px">
      <div style="padding: 20px 0;">
        <el-alert
          title="警告"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 20px;"
        >
          <template #default>
            <p>删除数据库是一个<strong style="color: #e6a23c;">不可逆操作</strong>，将会：</p>
            <ul style="margin: 10px 0; padding-left: 20px;">
              <li>永久删除数据库 <strong>{{ databaseToDelete?.name }}</strong></li>
              <li>删除该数据库中的所有表和数据</li>
              <li>无法恢复已删除的数据</li>
            </ul>
          </template>
        </el-alert>
        
        <p style="margin-bottom: 15px;">
          请输入数据库名称 <strong style="color: #f56c6c;">{{ databaseToDelete?.name }}</strong> 来确认删除：
        </p>
        
        <el-input
          v-model="deleteConfirmText"
          placeholder="请输入数据库名称"
          style="margin-bottom: 15px;"
          @keyup.enter="confirmDeleteDatabase"
        />
        
        <p style="font-size: 14px; color: #666;">
          <strong>数据库信息：</strong><br>
          名称：{{ databaseToDelete?.displayName }}<br>
          描述：{{ databaseToDelete?.description }}
        </p>
      </div>
      
      <template #footer>
        <el-button @click="cancelDeleteDatabase">取消</el-button>
        <el-button 
          type="danger" 
          @click="confirmDeleteDatabase"
          :loading="deletingDatabase"
          :disabled="deleteConfirmText !== databaseToDelete?.name"
        >
          确认删除
        </el-button>
             </template>
     </el-dialog>

    <!-- 表设计器对话框 -->
    <el-dialog v-model="createTableDialogVisible" title="表设计器" width="90%" :close-on-click-modal="false">
      <div class="table-designer">
        <!-- 基本信息 -->
        <el-card class="basic-info-card" shadow="never">
          <template #header>
            <div style="font-weight: 600;">基本信息</div>
          </template>
          
          <el-form :model="tableDesign" label-width="100px" :rules="tableRules" ref="tableFormRef">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="表名" prop="tableName">
                  <el-input 
                    v-model="tableDesign.tableName"
                    placeholder="请输入表名（如：user_info）"
                    maxlength="64"
                    show-word-limit
                  />
                  <div style="font-size: 12px; color: #666; margin-top: 5px;">
                    表名只能包含字母、数字、下划线，不能以数字开头
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="数据库">
                  <el-input v-model="selectedDatabase" disabled />
                </el-form-item>
              </el-col>
            </el-row>
            
            <el-form-item label="表注释">
              <el-input 
                v-model="tableDesign.tableComment"
                type="textarea"
                :rows="2"
                placeholder="请输入表的用途说明（可选）"
                maxlength="200"
                show-word-limit
              />
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 列设计 -->
        <el-card class="columns-card" shadow="never" style="margin-top: 20px;">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span style="font-weight: 600;">列设计</span>
              <el-button type="primary" size="small" @click="addColumn">
                <el-icon><Plus /></el-icon>
                添加列
              </el-button>
            </div>
          </template>
          
          <div v-if="tableDesign.columns.length === 0" class="empty-columns">
            <el-empty description="请添加至少一个列">
              <el-button type="primary" @click="addColumn">添加第一个列</el-button>
            </el-empty>
          </div>
          
          <div v-else class="columns-editor">
            <el-table :data="tableDesign.columns" style="width: 100%">
              <el-table-column label="列名" width="150">
                <template #default="scope">
                  <el-input 
                    v-model="scope.row.name"
                    placeholder="列名"
                    size="small"
                    @input="validateColumnName(scope.row)"
                  />
                  <div v-if="scope.row.nameError" style="color: #f56c6c; font-size: 12px; margin-top: 2px;">
                    {{ scope.row.nameError }}
                  </div>
                </template>
              </el-table-column>
              
              <el-table-column label="数据类型" width="130">
                <template #default="scope">
                  <el-select 
                    v-model="scope.row.dataType"
                    placeholder="选择类型"
                    size="small"
                    style="width: 100%;"
                    @change="onDataTypeChange(scope.row)"
                  >
                    <el-option-group
                      v-for="group in groupedDataTypes"
                      :key="group.category"
                      :label="group.category"
                    >
                      <el-option
                        v-for="type in group.types"
                        :key="type.name"
                        :label="type.displayName"
                        :value="type.name"
                      >
                        <span>{{ type.displayName }}</span>
                        <span style="float: right; color: #8492a6; font-size: 12px;">{{ type.name }}</span>
                      </el-option>
                    </el-option-group>
                  </el-select>
                </template>
              </el-table-column>
              
              <el-table-column label="长度" width="80">
                <template #default="scope">
                  <el-input 
                    v-model="scope.row.length"
                    placeholder="长度"
                    size="small"
                    type="number"
                    :disabled="!needsLength(scope.row.dataType)"
                  />
                </template>
              </el-table-column>
              
              <el-table-column label="小数位" width="80">
                <template #default="scope">
                  <el-input 
                    v-model="scope.row.decimals"
                    placeholder="小数位"
                    size="small"
                    type="number"
                    :disabled="!needsDecimals(scope.row.dataType)"
                  />
                </template>
              </el-table-column>
              
              <el-table-column label="约束" width="200">
                <template #default="scope">
                  <div style="display: flex; flex-wrap: wrap; gap: 5px;">
                    <el-checkbox v-model="scope.row.isPrimary" size="small" @change="onPrimaryKeyChange(scope.row)">
                      主键
                    </el-checkbox>
                    <el-checkbox v-model="scope.row.isNotNull" size="small">
                      非空
                    </el-checkbox>
                    <el-checkbox v-model="scope.row.isAutoIncrement" size="small" :disabled="!canAutoIncrement(scope.row)">
                      自增
                    </el-checkbox>
                    <el-checkbox v-model="scope.row.isForeignKey" size="small" @change="onForeignKeyChange(scope.row)">
                      外键
                    </el-checkbox>
                  </div>
                </template>
              </el-table-column>
              
              <!-- 外键配置列 -->
              <el-table-column label="外键配置" width="300" v-if="hasAnyForeignKey">
                <template #default="scope">
                  <div v-if="scope.row.isForeignKey" style="display: flex; flex-direction: column; gap: 5px;">
                    <el-select 
                      v-model="scope.row.referenceTable"
                      placeholder="选择引用表"
                      size="small"
                      style="width: 100%;"
                      @change="onReferenceTableChange(scope.row)"
                      clearable
                      filterable
                    >
                      <el-option
                        v-for="table in availableTables"
                        :key="table"
                        :label="table"
                        :value="table"
                      />
                    </el-select>
                    
                    <el-select 
                      v-model="scope.row.referenceColumn"
                      placeholder="选择引用列"
                      size="small"
                      style="width: 100%;"
                      :disabled="!scope.row.referenceTable"
                      clearable
                      filterable
                      @change="onReferenceColumnChange(scope.row)"
                    >
                      <el-option
                        v-for="column in getTableColumns(scope.row.referenceTable)"
                        :key="column.name"
                        :label="column.label"
                        :value="column.name"
                      />
                    </el-select>
                    
                    <div style="display: flex; gap: 3px;">
                      <el-select v-model="scope.row.onUpdate" size="small" style="flex: 1;">
                        <template #prefix>更新:</template>
                        <el-option label="RESTRICT" value="RESTRICT"/>
                        <el-option label="CASCADE" value="CASCADE"/>
                        <el-option label="SET NULL" value="SET NULL"/>
                        <el-option label="NO ACTION" value="NO ACTION"/>
                      </el-select>
                      
                      <el-select v-model="scope.row.onDelete" size="small" style="flex: 1;">
                        <template #prefix>删除:</template>
                        <el-option label="RESTRICT" value="RESTRICT"/>
                        <el-option label="CASCADE" value="CASCADE"/>
                        <el-option label="SET NULL" value="SET NULL"/>
                        <el-option label="NO ACTION" value="NO ACTION"/>
                      </el-select>
                    </div>
                  </div>
                </template>
              </el-table-column>
              
              <el-table-column label="默认值" width="120">
                <template #default="scope">
                  <el-input 
                    v-model="scope.row.defaultValue"
                    placeholder="默认值"
                    size="small"
                    :disabled="scope.row.isAutoIncrement"
                  />
                </template>
              </el-table-column>
              
              <el-table-column label="注释" min-width="120">
                <template #default="scope">
                  <el-input 
                    v-model="scope.row.comment"
                    placeholder="列注释"
                    size="small"
                  />
                </template>
              </el-table-column>
              
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="scope">
                  <div style="display: flex; gap: 5px;">
                    <el-button 
                      size="small" 
                      type="danger" 
                      :icon="Delete"
                      @click="removeColumn(scope.$index)"
                      circle
                    />
                    <el-button 
                      v-if="scope.$index > 0"
                      size="small" 
                      @click="moveColumnUp(scope.$index)"
                      circle
                    >
                      ↑
                    </el-button>
                    <el-button 
                      v-if="scope.$index < tableDesign.columns.length - 1"
                      size="small" 
                      @click="moveColumnDown(scope.$index)"
                      circle
                    >
                      ↓
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>

        <!-- SQL 预览 -->
        <el-card class="sql-preview-card" shadow="never" style="margin-top: 20px;">
          <template #header>
            <div style="font-weight: 600;">SQL 预览</div>
          </template>
          
          <el-input
            v-model="generatedSQL"
            type="textarea"
            :rows="8"
            readonly
            style="font-family: 'Monaco', 'Consolas', monospace;"
          />
        </el-card>
      </div>
      
      <template #footer>
        <el-button @click="cancelCreateTable">取消</el-button>
        <el-button @click="previewSQL" :disabled="!canGenerateSQL">预览 SQL</el-button>
        <el-button type="primary" @click="confirmCreateTable" :loading="creatingTable" :disabled="!canCreateTable">
          创建表
        </el-button>
      </template>
    </el-dialog>

    <!-- 字段搜索结果弹窗 -->
    <el-dialog 
      v-model="fieldSearchResultDialogVisible" 
      :title="`字段值搜索结果 - 共找到 ${fieldSearchResult.totalCount || 0} 个匹配的表`" 
      width="90%"
      draggable
      :close-on-click-modal="false"
    >
      <div v-if="fieldSearchResult.tables && fieldSearchResult.tables.length > 0">
        <div style="margin-bottom: 15px;">
          <el-alert
            :title="`搜索值：'${fieldSearchResult.searchValue}' | 匹配表数：${fieldSearchResult.totalCount}`"
            type="success"
            :closable="false"
            show-icon
          />
        </div>
        
        <el-table 
          :data="fieldSearchResult.tables" 
          style="width: 100%" 
          :max-height="500"
          stripe
        >
          <el-table-column prop="TABLE_NAME" label="表名" width="250" sortable />
          <el-table-column prop="MATCH_COUNT" label="匹配记录数" width="150">
            <template #default="scope">
              <el-tag type="warning">{{ scope.row.MATCH_COUNT }}</el-tag>
              <el-tag v-if="scope.row.IS_COMPLETE" type="success" size="small" style="margin-left: 5px;">
                完整
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="TABLE_ROWS" label="表总行数" width="120">
            <template #default="scope">
              {{ scope.row.TABLE_ROWS?.toLocaleString() || 'N/A' }}
            </template>
          </el-table-column>
          <el-table-column prop="DATA_LENGTH" label="数据大小" width="120">
            <template #default="scope">
              {{ formatBytes(scope.row.DATA_LENGTH) }}
            </template>
          </el-table-column>
          <el-table-column prop="CREATE_TIME" label="创建时间" width="180">
            <template #default="scope">
              {{ formatDate(scope.row.CREATE_TIME) }}
            </template>
          </el-table-column>
          <el-table-column prop="TABLE_COMMENT" label="注释" show-overflow-tooltip />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="scope">
              <el-button size="small" @click="viewSearchResultData(scope.row.TABLE_NAME)">
                查看数据
              </el-button>
              <el-button size="small" @click="viewTableStructure(scope.row.TABLE_NAME)">
                表结构
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      
      <el-empty v-else description="没有找到匹配的表">
        <template #image>
          <div style="font-size: 60px; color: #409eff;">
            <el-icon><Search /></el-icon>
          </div>
        </template>
      </el-empty>
      
      <template #footer>
        <div style="text-align: right;">
          <el-button @click="clearFieldSearch">清空搜索</el-button>
          <el-button type="primary" @click="fieldSearchResultDialogVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 多个字段搜索结果弹窗 -->
    <div v-for="dialog in searchDialogs" :key="dialog.id">
      <!-- 最小化状态显示 -->
      <div 
        v-if="dialog.minimized" 
        class="minimized-dialog" 
        @click="restoreDialog(dialog.id)"
        :style="{ 
          bottom: `${20 + (searchDialogs.findIndex(d => d.id === dialog.id && d.minimized) * 60)}px`,
          right: '20px',
          borderLeftColor: getDatabaseColor(dialog.result.dataSource),
          borderLeftWidth: '4px'
        }"
        :title="`点击展开搜索结果 | 搜索值: ${dialog.searchValue} | 数据库: ${dialog.result.dataSource || '未知'} | 匹配表数: ${dialog.result.totalCount}`"
      >
        <div class="minimized-content">
          <el-icon><Search /></el-icon>
          <span class="search-value">{{ dialog.searchValue }}</span>
          <span class="database-info">[{{ dialog.result.dataSource || '未知' }}]</span>
          <span class="match-count">{{ dialog.result.totalCount }}</span>
        </div>
        <el-button 
          type="danger" 
          size="small" 
          circle 
          @click.stop="closeDialog(dialog.id)"
          class="close-btn"
          title="关闭此搜索结果"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>

      <!-- 正常状态弹窗 -->
      <el-dialog 
        v-if="!dialog.minimized"
        v-model="dialog.visible" 
        :title="`字段值搜索结果 - '${dialog.searchValue}' (${dialog.result.totalCount || 0} 个匹配的表)`" 
        width="90%"
        draggable
        :close-on-click-modal="false"
        @close="closeDialog(dialog.id)"
      >
        <template #header="{ titleId, titleClass }">
          <div class="dialog-header">
            <h4 :id="titleId" :class="titleClass">
              字段值搜索结果 - '{{ dialog.searchValue }}' ({{ dialog.result.totalCount || 0 }} 个匹配的表)
            </h4>
            <div class="dialog-controls">
              <el-button 
                type="info" 
                size="small" 
                @click="minimizeDialog(dialog.id)"
                title="最小化"
              >
                <el-icon><Minus /></el-icon>
              </el-button>
            </div>
          </div>
        </template>

        <div v-if="dialog.result.tables && dialog.result.tables.length > 0">
          <div style="margin-bottom: 15px;">
            <el-alert
              :title="`搜索值：'${dialog.searchValue}' | 匹配表数：${dialog.result.totalCount}`"
              type="success"
              :closable="false"
              show-icon
            />
          </div>
          
          <el-table 
            :data="dialog.result.tables" 
            style="width: 100%" 
            :max-height="500"
            stripe
          >
            <el-table-column prop="TABLE_NAME" label="表名" width="250" sortable />
            <el-table-column prop="MATCH_COUNT" label="匹配记录数" width="150">
              <template #default="scope">
                <el-tag type="warning">{{ scope.row.MATCH_COUNT }}</el-tag>
                <el-tag v-if="scope.row.IS_COMPLETE" type="success" size="small" style="margin-left: 5px;">
                  完整
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="TABLE_ROWS" label="表总行数" width="120">
              <template #default="scope">
                {{ scope.row.TABLE_ROWS?.toLocaleString() || 'N/A' }}
              </template>
            </el-table-column>
            <el-table-column prop="DATA_LENGTH" label="数据大小" width="120">
              <template #default="scope">
                {{ formatBytes(scope.row.DATA_LENGTH) }}
              </template>
            </el-table-column>
            <el-table-column prop="CREATE_TIME" label="创建时间" width="180">
              <template #default="scope">
                {{ formatDate(scope.row.CREATE_TIME) }}
              </template>
            </el-table-column>
            <el-table-column prop="TABLE_COMMENT" label="注释" show-overflow-tooltip />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="scope">
                <el-button size="small" @click="viewSearchResultData(scope.row.TABLE_NAME, dialog.searchValue)">
                  查看数据
                </el-button>
                <el-button size="small" @click="viewTableStructure(scope.row.TABLE_NAME)">
                  表结构
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        
        <el-empty v-else description="没有找到匹配的表">
          <template #image>
            <div style="font-size: 60px; color: #409eff;">
              <el-icon><Search /></el-icon>
            </div>
          </template>
        </el-empty>
        
        <template #footer>
          <div style="text-align: right;">
            <el-button @click="clearSingleSearch(dialog.id)">清空此搜索</el-button>
            <el-button @click="clearAllSearches">清空所有搜索</el-button>
            <el-button type="primary" @click="closeDialog(dialog.id)">关闭</el-button>
          </div>
        </template>
      </el-dialog>
    </div>

    <!-- 搜索进度弹窗 -->
    <el-dialog 
      v-model="searchProgressDialogVisible" 
      title="字段值搜索进度" 
      width="600px"
      :close-on-click-modal="false"
      :show-close="false"
      center
    >
      <div class="search-progress-content">
        <div class="progress-header">
          <el-icon class="spinning-icon"><Loading /></el-icon>
          <span class="progress-title">正在搜索数据库中的表...</span>
        </div>
        
        <div class="progress-info">
          <div class="search-info">
            <p><strong>搜索值：</strong>{{ currentSearchValue }}</p>
            <p><strong>数据库：</strong>{{ getSelectedDatabaseInfo()?.displayName || selectedDatabase }}</p>
            <p><strong>搜索模式：</strong>
              <el-tag 
                :type="searchMode === 'auto' ? 'primary' : searchMode === 'text_only' ? 'success' : searchMode === 'numeric_only' ? 'warning' : 'info'" 
                size="small"
              >
                {{ 
                  searchMode === 'auto' ? '智能模式' : 
                  searchMode === 'text_only' ? '仅文本字段' : 
                  searchMode === 'numeric_only' ? '仅数字字段' : 
                  '全部字段' 
                }}
              </el-tag>
              <span style="color: #666; font-size: 12px; margin-left: 8px;">
                {{ 
                  searchMode === 'auto' ? '(根据搜索内容自动判断)' : 
                  searchMode === 'text_only' ? '(适合蛋白质名称等文本搜索)' : 
                  searchMode === 'numeric_only' ? '(仅搜索数字类型字段)' : 
                  '(搜索所有字段类型)' 
                }}
              </span>
            </p>
          </div>
          
          <div class="progress-details">
            <el-progress 
              :percentage="searchProgressPercentage" 
              :stroke-width="8"
              status="warning"
              striped
              striped-flow
            />
            <div class="progress-text">
              <span class="current-progress">{{ searchProgressText }}</span>
              <span class="time-info">已用时：{{ formatElapsedTime(searchElapsedTime) }}</span>
            </div>
          </div>
          
          <div v-if="currentSearchingTable" class="current-table">
            <p><strong>当前搜索表：</strong>{{ currentSearchingTable }}</p>
          </div>
          
          <div v-if="foundTablesCount > 0" class="found-info">
            <el-tag type="success">已找到 {{ foundTablesCount }} 个匹配的表</el-tag>
          </div>
        </div>
      </div>
      
      <template #footer>
        <div style="text-align: center;">
          <el-button @click="cancelSearch" type="danger" :loading="cancelling">
            取消搜索
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Search, Coin, Folder, FolderOpened, Refresh, Plus, Minus, Loading } from '@element-plus/icons-vue'
import api, { userState, checkPermission, databaseApi } from '../utils/api'

export default {
  name: 'Tables',
  components: {
    Delete,
    Edit,
    Search,
    Coin,
    Folder,
    FolderOpened,
    Refresh,
    Plus,
    Minus,
    Loading
  },
  setup() {
    const tableList = ref([])
    const selectedTable = ref('')
    const structureDialogVisible = ref(false)
    const dataDialogVisible = ref(false)
    const addDataDialogVisible = ref(false)
    const editDataDialogVisible = ref(false)
    const tableColumns = ref([])
    const tableData = ref([])
    const dataLimit = ref(100)
    const newRowData = ref({})
    const editRowData = ref({})
    const originalRowData = ref({})
    const submitting = ref(false)
    const hasPrimaryKey = ref(false)
    const tableDataLoading = ref(false)
    const searchQuery = ref('')
    const loadingTables = ref(false)
    const databaseInfo = ref(null)
    const selectedDatabase = ref('chembl33')
    const availableDatabases = ref([])
    
    // 字段值搜索相关的响应式变量
    const fieldSearchQuery = ref('')
    const searchMode = ref('auto') // 搜索模式：auto, text_only, numeric_only, all
    const fieldSearching = ref(false)
    const fieldSearchResult = ref({})
    const fieldSearchError = ref('')
    const fieldSearchResultDialogVisible = ref(false)
    
    // 多弹窗管理
    const searchDialogs = ref([])
    const nextDialogId = ref(1)
    
    // 搜索进度相关
    const searchProgressDialogVisible = ref(false)
    const currentSearchValue = ref('')
    const searchProgressPercentage = ref(0)
    const searchProgressText = ref('')
    const currentSearchingTable = ref('')
    const foundTablesCount = ref(0)
    const totalTablesCount = ref(0)
    const searchStartTime = ref(0)
    const searchElapsedTime = ref(0)
    const searchTimer = ref(null)
    const cancelling = ref(false)
    const currentSearchAbortController = ref(null)
    
    // 新建数据库相关
    const createDatabaseDialogVisible = ref(false)
    const creatingDatabase = ref(false)
    const databaseFormRef = ref(null)
    const newDatabaseForm = ref({
      databaseName: '',
      charset: 'utf8',
      collation: 'utf8_general_ci',
      description: ''
    })
    
    // 删除数据库相关
    const deleteDatabaseDialogVisible = ref(false)
    const deletingDatabase = ref(false)
    const databaseToDelete = ref(null)
    const deleteConfirmText = ref('')
    
    // 表设计器相关
    const createTableDialogVisible = ref(false)
    const creatingTable = ref(false)
    const tableFormRef = ref(null)
    const availableDataTypes = ref([])
    const tableDesign = ref({
      tableName: '',
      tableComment: '',
      columns: []
    })
    const generatedSQL = ref('')
    
    // 外键相关变量
    const availableTables = ref([])
    const referencedTableColumns = ref({})
    
    // 权限相关
    const canAccessCurrentDatabase = ref(true)
    const canModifyCurrentDatabase = ref(true)
    const canCreateDatabase = ref(false)
    const permissionMessage = ref('')
    
    // 表单验证规则
    const databaseRules = {
      databaseName: [
        { required: true, message: '请输入数据库名称', trigger: 'blur' },
        { 
          pattern: /^[a-zA-Z0-9_]+$/, 
          message: '数据库名称只能包含字母、数字和下划线', 
          trigger: 'blur' 
        },
        { min: 1, max: 64, message: '数据库名称长度在1到64个字符', trigger: 'blur' }
      ],
      charset: [
        { required: true, message: '请选择字符集', trigger: 'change' }
      ],
      collation: [
        { required: true, message: '请选择排序规则', trigger: 'change' }
      ]
    }
    
    // 表设计验证规则
    const tableRules = {
      tableName: [
        { required: true, message: '请输入表名', trigger: 'blur' },
        { 
          pattern: /^[a-zA-Z_][a-zA-Z0-9_]*$/, 
          message: '表名只能包含字母、数字、下划线，不能以数字开头', 
          trigger: 'blur' 
        },
        { min: 1, max: 64, message: '表名长度在1到64个字符', trigger: 'blur' }
      ]
    }
    
    // 权限检查函数
    const checkDatabasePermission = (database, operation = 'read') => {
      const permission = checkPermission(database, operation)
      if (!permission.allowed) {
        return {
          allowed: false,
          message: permission.reason
        }
      }
      return { allowed: true }
    }
    
    // 更新权限状态
    const updatePermissionState = () => {
      const readPermission = checkDatabasePermission(selectedDatabase.value, 'read')
      const writePermission = checkDatabasePermission(selectedDatabase.value, 'write')
      
      canAccessCurrentDatabase.value = readPermission.allowed
      canModifyCurrentDatabase.value = writePermission.allowed
      
      // 检查是否可以创建数据库（只有管理员可以）
      const userInfo = userState.getUserInfo()
      canCreateDatabase.value = userInfo.userType === 'admin'
      
      if (!readPermission.allowed) {
        permissionMessage.value = readPermission.message
      } else if (!writePermission.allowed) {
        permissionMessage.value = '您只有查看权限，无法修改数据'
      } else {
        permissionMessage.value = ''
      }
    }
    
    // 分页相关
    const pagination = ref({
      currentPage: 1,
      pageSize: 20,
      totalCount: 0,
      totalPages: 0
    })

    const loadTables = async (showMessage = true) => {
      try {
        loadingTables.value = true
        console.log('开始加载表列表，数据库:', selectedDatabase.value)
        
        // 检查权限
        const permission = checkDatabasePermission(selectedDatabase.value, 'read')
        if (!permission.allowed) {
          console.warn('无权限访问数据库:', permission.message)
          tableList.value = []
          if (showMessage) {
            ElMessage.warning(`无权限访问数据库: ${permission.message}`)
          }
          return
        }
        
        console.log('权限检查通过，发起API请求...')
        const response = await api.get('/api/database/tables', {
          params: {
            dataSource: selectedDatabase.value
          }
        })
        
        console.log('API请求成功，获取到表数据:', response.data?.length || 0, '个表')
        tableList.value = response.data
        
        // 异步加载数据库统计信息
        loadDatabaseInfo()
        
        // 只在用户主动刷新时显示成功消息
        if (showMessage) {
          ElMessage.success(`已刷新数据库 ${selectedDatabase.value}，共 ${response.data?.length || 0} 个表`)
        }
      } catch (error) {
        console.error('加载表列表失败:', error)
        const errorMsg = error.response?.data?.error || error.message
        
        // 处理权限错误 - 显示错误消息给用户
        if (error.response?.status === 403) {
          console.warn('权限不足：' + errorMsg)
          tableList.value = []
          if (showMessage) {
            ElMessage.error('权限不足: ' + errorMsg)
          }
        } else if (error.response?.status === 401) {
          if (showMessage) {
            ElMessage.error('用户未登录，请重新登录')
          }
        } else {
          if (showMessage) {
            ElMessage.error('加载表列表失败: ' + errorMsg)
          }
        }
      } finally {
        loadingTables.value = false
        console.log('表列表加载完成')
      }
    }

    const selectTable = (row) => {
      selectedTable.value = row.TABLE_NAME
    }

    const viewTableStructure = async (tableName) => {
      try {
        selectedTable.value = tableName
        const response = await api.get(`/api/database/tables/${tableName}/columns`, {
          params: {
            dataSource: selectedDatabase.value
          }
        })
        tableColumns.value = response.data
        structureDialogVisible.value = true
      } catch (error) {
        console.error('加载表结构失败:', error)
      }
    }

    const viewTableData = async (tableName) => {
      selectedTable.value = tableName
      dataDialogVisible.value = true
      
      // 立即清空表数据并显示加载状态
      tableData.value = []
      tableDataLoading.value = true
      
      // 重置分页状态
      pagination.value.currentPage = 1
      pagination.value.pageSize = 20
      pagination.value.totalCount = 0
      pagination.value.totalPages = 0
      
      // 清空搜索结果状态
      fieldSearchResult.value = {}
      
      // 同时获取表结构（删除功能需要主键信息）
      try {
        const response = await api.get(`/api/database/tables/${tableName}/columns`, {
          params: {
            dataSource: selectedDatabase.value
          }
        })
        tableColumns.value = response.data
      } catch (error) {
        console.error('获取表结构失败:', error)
      }
      
      await loadTableDataWithPagination()
    }

    const loadTableData = async () => {
      try {
        const response = await api.get(`/api/database/tables/${selectedTable.value}/data?limit=${dataLimit.value}`)
        tableData.value = response.data
      } catch (error) {
        console.error('加载表数据失败:', error)
      }
    }

    // 分页加载表数据
    const loadTableDataWithPagination = async () => {
      try {
        // 立即清空表数据并显示加载状态
        tableData.value = []
        tableDataLoading.value = true
        
        // 检查是否是搜索结果数据
        if (fieldSearchResult.value && fieldSearchResult.value.searchValue) {
          // 加载搜索结果数据（支持分页）
          await loadSearchResultDataWithPagination()
        } else {
          // 加载普通表数据
          const response = await api.get(`/api/database/tables/${selectedTable.value}/data/page`, {
            params: {
              page: pagination.value.currentPage,
              size: pagination.value.pageSize,
              dataSource: selectedDatabase.value
            }
          })
          
          const result = response.data
          
          // 检查是否有错误信息
          if (result.error) {
            ElMessage.error('加载数据失败: ' + result.error)
            return
          }
          
          tableData.value = result.data || []
          pagination.value.totalCount = result.totalCount || 0
          pagination.value.totalPages = result.totalPages || 0
          pagination.value.currentPage = result.currentPage || 1
          pagination.value.pageSize = result.pageSize || 20
        }
        
      } catch (error) {
        console.error('加载表数据失败:', error)
        const errorMsg = error.response?.data?.error || error.message
        
        // 处理超时错误
        if (errorMsg.includes('timeout') || errorMsg.includes('exceeded')) {
          ElMessage.error('请求超时，请稍后重试')
        } else if (errorMsg.includes('连接') || errorMsg.includes('network') || errorMsg.includes('Network')) {
          ElMessage.error('网络连接异常，请检查网络状态后重试')
        } else {
          ElMessage.error('加载数据失败: ' + errorMsg)
        }
        
        // 重置数据
        tableData.value = []
        pagination.value.totalCount = 0
        pagination.value.totalPages = 0
      } finally {
        tableDataLoading.value = false
      }
    }

    // 分页加载搜索结果数据
    const loadSearchResultDataWithPagination = async () => {
      const userInfo = userState.getUserInfo()
      if (!userInfo.userId || !userInfo.userType) {
        throw new Error('用户未登录，无法加载搜索结果数据')
      }
      
      const response = await api.get('/api/database/search/data-by-value', {
        params: {
          tableName: selectedTable.value,
          searchValue: fieldSearchResult.value.searchValue,
          dataSource: selectedDatabase.value,
          page: pagination.value.currentPage,
          size: pagination.value.pageSize,
          userId: userInfo.userId,
          userType: userInfo.userType
        },
        timeout: 600000 // 10分钟超时
      })
      
      const searchData = response.data
      
      // 检查是否有错误信息
      if (searchData.error) {
        ElMessage.error('加载搜索结果数据失败: ' + searchData.error)
        return
      }
      
      tableData.value = searchData.data || []
      pagination.value.totalCount = searchData.totalCount || 0
      pagination.value.totalPages = searchData.totalPages || Math.ceil((searchData.totalCount || 0) / pagination.value.pageSize)
      pagination.value.currentPage = searchData.currentPage || pagination.value.currentPage
      pagination.value.pageSize = searchData.pageSize || pagination.value.pageSize
    }

    // 分页事件处理
    const handleSizeChange = (newSize) => {
      pagination.value.pageSize = newSize
      pagination.value.currentPage = 1
      loadTableDataWithPagination()
    }

    const handleCurrentChange = (newPage) => {
      pagination.value.currentPage = newPage
      loadTableDataWithPagination()
    }

    const changePageSize = () => {
      pagination.value.currentPage = 1
      loadTableDataWithPagination()
    }

    // 字段值搜索相关方法
    const searchTablesByFieldValue = async () => {
      if (!fieldSearchQuery.value.trim()) {
        fieldSearchError.value = '搜索值不能为空'
        return
      }

      // 检查用户登录状态
      const userInfo = userState.getUserInfo()
      if (!userInfo.userId || !userInfo.userType) {
        fieldSearchError.value = '请先登录后再进行搜索'
        return
      }

      // 立即显示进度弹窗
      showSearchProgressDialog(fieldSearchQuery.value.trim())

      fieldSearching.value = true
      fieldSearchError.value = ''
      fieldSearchResult.value = {}

      try {
        // 使用SSE接收实时进度，传递搜索模式参数
        await startRealTimeSearch(fieldSearchQuery.value, selectedDatabase.value, userInfo.userId, userInfo.userType, searchMode.value)
      } catch (error) {
        closeSearchProgressDialog()
        
        // 如果是用户取消的请求，不显示错误信息
        if (error.message && error.message.includes('canceled')) {
          ElMessage.info('搜索已取消')
          return
        }
        
        fieldSearchError.value = error.message || '字段值搜索失败'
        ElMessage.error(fieldSearchError.value)
      } finally {
        fieldSearching.value = false
      }
    }

    // 创建新的搜索弹窗
    const createNewSearchDialog = (searchValue, result) => {
      const newDialog = {
        id: nextDialogId.value++,
        searchValue: searchValue,
        result: result,
        visible: true,
        minimized: false
      }
      searchDialogs.value.push(newDialog)
    }

    // 最小化弹窗
    const minimizeDialog = (dialogId) => {
      const dialog = searchDialogs.value.find(d => d.id === dialogId)
      if (dialog) {
        dialog.minimized = true
        dialog.visible = false
      }
    }

    // 恢复弹窗
    const restoreDialog = (dialogId) => {
      const dialog = searchDialogs.value.find(d => d.id === dialogId)
      if (dialog) {
        dialog.minimized = false
        dialog.visible = true
      }
    }

    // 关闭弹窗
    const closeDialog = (dialogId) => {
      const index = searchDialogs.value.findIndex(d => d.id === dialogId)
      if (index !== -1) {
        searchDialogs.value.splice(index, 1)
      }
    }

    // 清空单个搜索
    const clearSingleSearch = (dialogId) => {
      closeDialog(dialogId)
    }

    // 清空所有搜索
    const clearAllSearches = () => {
      searchDialogs.value = []
    }

    // 清空特定数据库的搜索结果
    const clearSearchesByDatabase = (databaseName) => {
      if (!databaseName) return
      
      // 只保留不是来自指定数据库的搜索弹窗
      searchDialogs.value = searchDialogs.value.filter(dialog => {
        // 获取搜索结果的数据库来源
        const dialogDataSource = dialog.result?.dataSource 
        
        // 如果没有数据库信息，则保留（可能是老的搜索结果）
        if (!dialogDataSource) {
          console.log('保留没有数据库信息的搜索结果:', dialog.searchValue)
          return true
        }
        
        // 只有当数据库来源不同时才保留
        const shouldKeep = dialogDataSource !== databaseName
        if (!shouldKeep) {
          console.log('清空来自数据库', databaseName, '的搜索结果:', dialog.searchValue)
        }
        return shouldKeep
      })
    }

    // 搜索进度相关方法
    const showSearchProgressDialog = (searchValue) => {
      currentSearchValue.value = searchValue
      searchProgressDialogVisible.value = true
      searchProgressPercentage.value = 0
      searchProgressText.value = '准备开始搜索...'
      currentSearchingTable.value = ''
      foundTablesCount.value = 0
      totalTablesCount.value = 0
      searchStartTime.value = Date.now()
      searchElapsedTime.value = 0
      cancelling.value = false
      
      // 启动计时器
      startElapsedTimeTimer()
    }

    const closeSearchProgressDialog = () => {
      searchProgressDialogVisible.value = false
      stopElapsedTimeTimer()
      if (searchTimer.value) {
        clearTimeout(searchTimer.value)
        searchTimer.value = null
      }
    }

    // SSE实时搜索方法
    const startRealTimeSearch = (searchValue, dataSource, userId, userType, searchMode = 'auto') => {
      return new Promise((resolve, reject) => {
        const baseUrl = api.defaults.baseURL || ''
        const url = `${baseUrl}/api/database/search/tables-by-value-progress?` + new URLSearchParams({
          searchValue,
          dataSource,
          userId: userId.toString(),
          userType,
          searchMode
        })

        const eventSource = new EventSource(url)
        currentSearchAbortController.value = { eventSource }
        let searchResult = null
        let isCompleted = false // 添加标志来跟踪是否正常完成

        // 监听各种事件
        eventSource.addEventListener('start', (event) => {
          const data = JSON.parse(event.data)
          console.log('搜索开始:', data)
          searchProgressText.value = '正在准备搜索...'
        })

        eventSource.addEventListener('total', (event) => {
          const data = JSON.parse(event.data)
          totalTablesCount.value = data.totalTables
          console.log('总表数:', data.totalTables)
        })

        eventSource.addEventListener('progress', (event) => {
          const data = JSON.parse(event.data)
          currentSearchingTable.value = data.currentTable
          searchProgressPercentage.value = data.percentage
          searchProgressText.value = `${data.searchedCount}/${data.totalCount}`
          foundTablesCount.value = data.foundCount
          
          console.log('搜索进度:', data)
        })

        eventSource.addEventListener('found', (event) => {
          const data = JSON.parse(event.data)
          foundTablesCount.value = data.foundCount
          console.log('找到匹配表:', data.table.TABLE_NAME)
        })

        eventSource.addEventListener('complete', (event) => {
          const data = JSON.parse(event.data)
          console.log('搜索完成:', data)
          
          isCompleted = true // 标记为正常完成
          
          searchProgressText.value = `${data.searchedCount}/${data.searchedCount}`
          searchProgressPercentage.value = 100
          currentSearchingTable.value = '搜索完成'
          foundTablesCount.value = data.foundCount
          
          // 保存搜索结果
          searchResult = data
          fieldSearchResult.value = data
          
          // 关闭进度弹窗
          setTimeout(() => {
            closeSearchProgressDialog()
            
            // 显示结果弹窗
            if (data.tables && data.tables.length > 0) {
              createNewSearchDialog(searchValue, data)
            } else {
              ElMessage.info('没有找到包含该值的表')
            }
            
            eventSource.close()
            resolve(searchResult)
          }, 1000)
        })

        eventSource.addEventListener('error', (event) => {
          console.error('SSE错误:', event)
          const data = JSON.parse(event.data)
          eventSource.close()
          reject(new Error(data.message || '搜索过程中发生错误'))
        })

        eventSource.addEventListener('timeout', (event) => {
          const data = JSON.parse(event.data)
          console.warn('搜索超时:', data)
          ElMessage.warning(`搜索超时，已搜索 ${data.searchedCount} 个表，找到 ${data.foundCount} 个匹配表`)
          eventSource.close()
          resolve(searchResult)
        })

        eventSource.addEventListener('table_error', (event) => {
          const data = JSON.parse(event.data)
          console.warn('表搜索错误:', data)
        })

        eventSource.onerror = () => {
          // 只有在非正常完成的情况下才报告错误
          if (!isCompleted) {
            console.error('EventSource连接错误')
            eventSource.close()
            reject(new Error('网络连接错误，请重试'))
          }
        }
      })
    }

    const startElapsedTimeTimer = () => {
      const timer = setInterval(() => {
        if (!searchProgressDialogVisible.value) {
          clearInterval(timer)
          return
        }
        searchElapsedTime.value = Date.now() - searchStartTime.value
      }, 1000)
    }

    const stopElapsedTimeTimer = () => {
      // 计时器会在下次更新时自动停止
    }

    const formatElapsedTime = (ms) => {
      const seconds = Math.floor(ms / 1000)
      const minutes = Math.floor(seconds / 60)
      const remainingSeconds = seconds % 60
      
      if (minutes > 0) {
        return `${minutes}分${remainingSeconds}秒`
      } else {
        return `${remainingSeconds}秒`
      }
    }

    const cancelSearch = () => {
      cancelling.value = true
      
      // 关闭SSE连接
      if (currentSearchAbortController.value && currentSearchAbortController.value.eventSource) {
        currentSearchAbortController.value.eventSource.close()
        currentSearchAbortController.value = null
      }
      
      // 关闭进度弹窗
      closeSearchProgressDialog()
      
      // 重置搜索状态
      fieldSearching.value = false
      ElMessage.info('搜索已取消')
    }

    const viewSearchResultData = async (tableName, searchValue = null) => {
      // 检查用户登录状态
      const userInfo = userState.getUserInfo()
      if (!userInfo.userId || !userInfo.userType) {
        console.error('用户未登录，无法查看表数据')
        return
      }

      try {
        selectedTable.value = tableName
        dataDialogVisible.value = true
        
        // 立即清空表数据并显示加载状态
        tableData.value = []
        tableDataLoading.value = true
        
        // 重置分页状态
        pagination.value.currentPage = 1
        pagination.value.pageSize = 20
        pagination.value.totalCount = 0
        pagination.value.totalPages = 0
        
        // 获取表结构
        const structureResponse = await api.get(`/api/database/tables/${tableName}/columns`, {
          params: {
            dataSource: selectedDatabase.value
          }
        })
        tableColumns.value = structureResponse.data

        // 如果有搜索值，获取包含搜索值的数据
        if (searchValue) {
          // 设置搜索结果用于高亮显示
          fieldSearchResult.value = { searchValue: searchValue }
          
          // 使用分页加载搜索结果数据
          await loadTableDataWithPagination()
        } else {
          // 如果没有搜索值，加载普通表数据
          fieldSearchResult.value = {}
          await loadTableDataWithPagination()
        }
        
      } catch (error) {
        console.error('查看搜索结果数据失败:', error)
        ElMessage.error('查看搜索结果数据失败: ' + (error.response?.data?.error || error.message))
        
        // 发生错误时也要关闭加载状态
        tableDataLoading.value = false
        tableData.value = []
      }
    }

    const clearFieldSearch = () => {
      fieldSearchQuery.value = ''
      fieldSearchResult.value = {}
      fieldSearchError.value = ''
      fieldSearchResultDialogVisible.value = false
    }

    // 判断单元格值是否包含搜索值
    const cellContainsSearchValue = (cellValue, searchValue) => {
      if (!cellValue || !searchValue) return false
      return String(cellValue).toLowerCase().includes(String(searchValue).toLowerCase())
    }

    const formatBytes = (bytes) => {
      if (!bytes) return '0 B'
      const k = 1024
      const sizes = ['B', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
    }

    const formatDate = (dateStr) => {
      if (!dateStr) return 'N/A'
      return new Date(dateStr).toLocaleString('zh-CN')
    }

    const getColumnWidth = (columnName) => {
      // 根据列名长度和数据类型调整列宽
      const baseWidth = Math.max(columnName.length * 10, 100)
      return Math.min(baseWidth, 200)
    }
    
    const showAddDataDialog = async () => {
      // 检查写入权限
      const permission = checkDatabasePermission(selectedDatabase.value, 'write')
      if (!permission.allowed) {
        ElMessage.error(permission.message)
        return
      }
      
      try {
        // 先获取表结构
        const response = await api.get(`/api/database/tables/${selectedTable.value}/columns`, {
          params: {
            dataSource: selectedDatabase.value
          }
        })
        tableColumns.value = response.data
        
        // 检查是否有主键
        hasPrimaryKey.value = tableColumns.value.some(col => col.COLUMN_KEY === 'PRI')
        
        // 初始化新行数据
        newRowData.value = {}
        tableColumns.value.forEach(column => {
          if (column.COLUMN_DEFAULT !== null) {
            newRowData.value[column.COLUMN_NAME] = column.COLUMN_DEFAULT
          } else {
            newRowData.value[column.COLUMN_NAME] = ''
          }
        })
        
        addDataDialogVisible.value = true
      } catch (error) {
        console.error('获取表结构失败:', error)
        ElMessage.error('获取表结构失败')
      }
    }

    const submitNewData = async () => {
      try {
        submitting.value = true
        
        // 验证必填字段
        const requiredFields = tableColumns.value.filter(col => 
          col.IS_NULLABLE === 'NO' && !col.EXTRA.includes('auto_increment')
        )
        
        for (const field of requiredFields) {
          const value = newRowData.value[field.COLUMN_NAME]
          if (!value || value.toString().trim() === '') {
            ElMessage.error(`字段 ${field.COLUMN_NAME} 是必填项`)
            return
          }
        }
        
        // 过滤掉空值和自增字段
        const dataToSubmit = {}
        tableColumns.value.forEach(column => {
          const value = newRowData.value[column.COLUMN_NAME]
          if (!column.EXTRA.includes('auto_increment') && value !== '' && value !== null) {
            dataToSubmit[column.COLUMN_NAME] = value
          }
        })

        const response = await api.post(`/api/database/tables/${selectedTable.value}/data`, {
          dataSource: selectedDatabase.value,
          data: dataToSubmit
        })
        
        if (response.data.success) {
          ElMessage.success(response.data.message)
          addDataDialogVisible.value = false
          // 刷新表数据
          await loadTableDataWithPagination()
        } else {
          ElMessage.error(response.data.error || '数据插入失败')
        }
      } catch (error) {
        const errorMsg = error.response?.data?.error || error.message
        
        // 提供更友好的错误信息
        let friendlyMessage = '数据插入失败'
        
        if (errorMsg) {
          if (errorMsg.includes('数据重复') || errorMsg.includes('主键重复')) {
            friendlyMessage = errorMsg // 后端已经提供了友好的错误信息
          } else if (errorMsg.includes('必填字段缺失') || errorMsg.includes('cannot be null')) {
            friendlyMessage = errorMsg.includes('必填字段缺失') ? errorMsg : '必填字段不能为空，请检查表单'
          } else if (errorMsg.includes('数据长度超限') || errorMsg.includes('Data too long')) {
            friendlyMessage = errorMsg.includes('数据长度超限') ? errorMsg : '输入的数据过长，请缩短内容'
          } else if (errorMsg.includes('外键约束失败')) {
            friendlyMessage = errorMsg
          } else if (errorMsg.includes('数据格式错误')) {
            friendlyMessage = errorMsg
          } else if (errorMsg.includes('权限不足')) {
            friendlyMessage = '权限不足：您没有向此表插入数据的权限'
          } else if (errorMsg.includes('表不存在')) {
            friendlyMessage = '目标表不存在或已被删除，请刷新页面重试'
          } else if (errorMsg.includes('连接失败') || errorMsg.includes('timeout')) {
            friendlyMessage = '数据库连接超时，请稍后重试'
          } else if (errorMsg.includes('Duplicate entry')) {
            // 处理原始的MySQL错误信息
            const match = errorMsg.match(/Duplicate entry '(.+?)' for key '(.+?)'/);
            if (match) {
              const keyName = match[2].replace(/^.*\./, ''); // 移除表名前缀
              friendlyMessage = `数据重复：${keyName === 'PRIMARY' ? '主键' : `字段 '${keyName}'`} 的值 "${match[1]}" 已存在，请使用不同的值`
            } else {
              friendlyMessage = '数据重复：该记录已存在，请检查主键或唯一字段的值'
            }
          } else {
            friendlyMessage = '数据插入失败：' + errorMsg
          }
        }
        
        ElMessage.error(friendlyMessage)
      } finally {
        submitting.value = false
      }
    }

    const getColumnPlaceholder = (column) => {
      if (column.EXTRA.includes('auto_increment')) {
        return '自动生成'
      }
      if (column.COLUMN_KEY === 'PRI') {
        return '主键（不可修改）'
      }
      if (column.COLUMN_DEFAULT !== null) {
        return `默认值: ${column.COLUMN_DEFAULT}`
      }
      if (column.IS_NULLABLE === 'YES') {
        return '可选填'
      }
      return '必填'
    }

    const getColumnTagType = (column) => {
      if (column.COLUMN_KEY === 'PRI') return 'warning'
      if (column.EXTRA.includes('auto_increment')) return 'info'
      if (column.IS_NULLABLE === 'NO') return 'danger'
      return 'success'
    }

    const getColumnDescription = (column) => {
      let desc = []
      if (column.COLUMN_KEY === 'PRI') desc.push('主键')
      if (column.EXTRA.includes('auto_increment')) desc.push('自动递增')
      if (column.IS_NULLABLE === 'NO') desc.push('必填')
      if (column.COLUMN_COMMENT) desc.push(column.COLUMN_COMMENT)
      return desc.join(' | ')
    }

    const confirmDeleteRow = async (row) => {
      // 检查写入权限
      const permission = checkDatabasePermission(selectedDatabase.value, 'write')
      if (!permission.allowed) {
        ElMessage.error(permission.message)
        return
      }
      
      try {
        // 获取主键字段，用于构建删除条件
        const primaryKeys = tableColumns.value.filter(col => col.COLUMN_KEY === 'PRI')
        
        if (primaryKeys.length === 0) {
          ElMessage.warning('此表没有主键，无法安全删除数据')
          return
        }

        // 构建显示的主键值信息
        const keyInfo = primaryKeys.map(pk => `${pk.COLUMN_NAME}: ${row[pk.COLUMN_NAME]}`).join(', ')
        
        const result = await ElMessageBox.confirm(
          `确定要删除这条数据吗？\n主键: ${keyInfo}`,
          '确认删除',
          {
            confirmButtonText: '确定删除',
            cancelButtonText: '取消',
            type: 'warning',
            dangerouslyUseHTMLString: false
          }
        )

        if (result === 'confirm') {
          await deleteRow(row)
        }
      } catch (error) {
        // 用户取消删除，不需要处理
        if (error !== 'cancel') {
          console.error('删除确认出错:', error)
        }
      }
    }

    const deleteRow = async (row) => {
      try {
        // 构建删除条件（使用主键）
        const primaryKeys = tableColumns.value.filter(col => col.COLUMN_KEY === 'PRI')
        const whereConditions = {}
        
        primaryKeys.forEach(pk => {
          whereConditions[pk.COLUMN_NAME] = row[pk.COLUMN_NAME]
        })

        const response = await api.delete(`/api/database/tables/${selectedTable.value}/data`, {
          data: {
            dataSource: selectedDatabase.value,
            whereConditions: whereConditions
          }
        })
        
        if (response.data.success) {
          ElMessage.success(response.data.message)
          // 刷新表数据
          await loadTableDataWithPagination()
        } else {
          ElMessage.warning(response.data.message || '删除失败')
        }
      } catch (error) {
        const errorMsg = error.response?.data?.error || error.message
        ElMessage.error('删除失败: ' + errorMsg)
      }
    }

    const editRow = (row) => {
      // 检查写入权限
      const permission = checkDatabasePermission(selectedDatabase.value, 'write')
      if (!permission.allowed) {
        ElMessage.error(permission.message)
        return
      }
      
      // 深拷贝原始数据
      originalRowData.value = JSON.parse(JSON.stringify(row))
      editRowData.value = JSON.parse(JSON.stringify(row))
      editDataDialogVisible.value = true
    }

    const submitEditData = async () => {
      try {
        submitting.value = true
        
        // 获取主键字段，用于构建WHERE条件
        const primaryKeys = tableColumns.value.filter(col => col.COLUMN_KEY === 'PRI')
        
        if (primaryKeys.length === 0) {
          ElMessage.warning('此表没有主键，无法更新数据')
          return
        }

        // 构建WHERE条件（使用主键）
        const whereConditions = {}
        primaryKeys.forEach(pk => {
          whereConditions[pk.COLUMN_NAME] = originalRowData.value[pk.COLUMN_NAME]
        })

        // 找出修改的字段
        const updateData = {}
        for (const column of tableColumns.value) {
          const columnName = column.COLUMN_NAME
          const originalValue = originalRowData.value[columnName]
          const newValue = editRowData.value[columnName]
          
          // 跳过主键和自增字段
          if (column.COLUMN_KEY === 'PRI' || column.EXTRA.includes('auto_increment')) {
            continue
          }
          
          // 检查值是否有变化
          if (originalValue !== newValue) {
            updateData[columnName] = newValue
          }
        }

        // 如果没有修改任何字段
        if (Object.keys(updateData).length === 0) {
          ElMessage.info('没有数据被修改')
          editDataDialogVisible.value = false
          return
        }

        const response = await api.put(`/api/database/tables/${selectedTable.value}/data`, {
          dataSource: selectedDatabase.value,
          updateData: updateData,
          whereConditions: whereConditions
        })
        
        if (response.data.success) {
          ElMessage.success(response.data.message)
          editDataDialogVisible.value = false
          // 刷新表数据
          await loadTableDataWithPagination()
        } else {
          ElMessage.warning(response.data.message || '更新失败')
        }
      } catch (error) {
        const errorMsg = error.response?.data?.error || error.message
        
        // 提供更友好的错误信息
        let friendlyMessage = '数据更新失败'
        
        if (errorMsg) {
          if (errorMsg.includes('数据重复') || errorMsg.includes('主键重复')) {
            friendlyMessage = errorMsg // 后端已经提供了友好的错误信息
          } else if (errorMsg.includes('必填字段缺失') || errorMsg.includes('cannot be null')) {
            friendlyMessage = errorMsg.includes('必填字段缺失') ? errorMsg : '必填字段不能为空，请检查表单'
          } else if (errorMsg.includes('数据长度超限') || errorMsg.includes('Data too long')) {
            friendlyMessage = errorMsg.includes('数据长度超限') ? errorMsg : '输入的数据过长，请缩短内容'
          } else if (errorMsg.includes('外键约束失败')) {
            friendlyMessage = errorMsg + '，无法更新此记录'
          } else if (errorMsg.includes('数据格式错误')) {
            friendlyMessage = errorMsg
          } else if (errorMsg.includes('权限不足')) {
            friendlyMessage = '权限不足：您没有修改此表数据的权限'
          } else if (errorMsg.includes('Duplicate entry')) {
            // 处理原始的MySQL错误信息
            const match = errorMsg.match(/Duplicate entry '(.+?)' for key '(.+?)'/);
            if (match) {
              const keyName = match[2].replace(/^.*\./, ''); // 移除表名前缀
              friendlyMessage = `数据重复：${keyName === 'PRIMARY' ? '主键' : `字段 '${keyName}'`} 的值 "${match[1]}" 已存在，请使用不同的值`
            } else {
              friendlyMessage = '数据重复：该值已存在，请检查唯一字段的值'
            }
          } else {
            friendlyMessage = '数据更新失败：' + errorMsg
          }
        }
        
        ElMessage.error(friendlyMessage)
      } finally {
        submitting.value = false
      }
    }

          const switchDatabase = () => {
        // 实现切换数据库的逻辑
        console.log('切换到数据库:', selectedDatabase.value)
        
        // 清空当前页面的字段搜索结果（不影响最小化的搜索弹窗）
        clearFieldSearch()
        
        // 重置数据库信息
        databaseInfo.value = null
        
        // 更新权限状态
        updatePermissionState()
        
        // 如果有访问权限，则加载数据
        if (canAccessCurrentDatabase.value) {
          loadTables(false) // 切换数据库时不显示提示信息
          loadDatabaseInfo()
        } else {
          // 清空表列表
          tableList.value = []
          databaseInfo.value = null
        }
        
        console.log('切换数据库完成，保留了', searchDialogs.value.length, '个搜索结果弹窗')
    }
    
    // 显示新建数据库对话框
    const showCreateDatabaseDialog = () => {
      // 重置表单
      newDatabaseForm.value = {
        databaseName: '',
        charset: 'utf8',
        collation: 'utf8_general_ci',
        description: ''
      }
      createDatabaseDialogVisible.value = true
    }
    
    // 创建数据库
    const createDatabase = async () => {
      if (!databaseFormRef.value) return
      
      try {
        // 表单验证
        const valid = await databaseFormRef.value.validate()
        if (!valid) return
        
        creatingDatabase.value = true
        
        const userInfo = userState.getUserInfo()
        const requestData = {
          ...newDatabaseForm.value,
          userId: userInfo.userId,
          userType: userInfo.userType
        }
        
        const response = await api.post('/api/database/create', requestData)
        
        if (response.data.success) {
          ElMessage.success(response.data.message)
          createDatabaseDialogVisible.value = false
          
          // 将新创建的数据库添加到本地数据库列表
          const newDatabase = {
            name: response.data.databaseName,
            displayName: response.data.databaseName,
            description: response.data.description || '用户创建的数据库',
            type: response.data.type || 'mysql',
            status: response.data.status || 'active',
            charset: response.data.charset,
            collation: response.data.collation
          }
          
          // 添加到数据库列表（如果不存在的话）
          const existingIndex = availableDatabases.value.findIndex(db => db.name === newDatabase.name)
          if (existingIndex === -1) {
            availableDatabases.value.push(newDatabase)
          } else {
            availableDatabases.value[existingIndex] = newDatabase
          }
          
          // 切换到新创建的数据库
          selectedDatabase.value = newDatabase.name
          switchDatabase()
          
          // 在后台刷新数据库列表以获取最新信息
          loadAvailableDatabases().catch(err => {
            console.warn('后台刷新数据库列表失败:', err)
          })
        } else {
          ElMessage.error(response.data.error || '创建数据库失败')
        }
        
      } catch (error) {
        console.error('创建数据库失败:', error)
        const errorMsg = error.response?.data?.error || error.message
        ElMessage.error('创建数据库失败: ' + errorMsg)
      } finally {
        creatingDatabase.value = false
      }
    }
    
    // 检查数据库是否可以删除（只有用户创建的数据库可以删除）
    const canDeleteDatabase = (database) => {
      const systemDatabases = ['chembl33', 'tcrd6124expr2', 'login', 'information_schema', 'mysql', 'performance_schema', 'sys']
      return !systemDatabases.includes(database.name.toLowerCase())
    }
    
    // 显示删除数据库确认对话框
    const showDeleteDatabaseDialog = (database) => {
      databaseToDelete.value = database
      deleteConfirmText.value = ''
      deleteDatabaseDialogVisible.value = true
    }
    
    // 取消删除数据库
    const cancelDeleteDatabase = () => {
      databaseToDelete.value = null
      deleteConfirmText.value = ''
      deleteDatabaseDialogVisible.value = false
    }
    
    // 确认删除数据库
    const confirmDeleteDatabase = async () => {
      if (!databaseToDelete.value || deleteConfirmText.value !== databaseToDelete.value.name) {
        ElMessage.error('请正确输入数据库名称以确认删除')
        return
      }
      
      try {
        deletingDatabase.value = true
        
        const userInfo = userState.getUserInfo()
        const requestData = {
          databaseName: databaseToDelete.value.name,
          dataSource: 'chembl33', // 使用默认数据源
          userId: userInfo.userId,
          userType: userInfo.userType
        }
        
        const response = await api.delete('/api/database/drop', {
          data: requestData
        })
        
        if (response.data.success) {
          ElMessage.success(response.data.message)
          
          // 从本地数据库列表中移除已删除的数据库
          const index = availableDatabases.value.findIndex(db => db.name === databaseToDelete.value.name)
          if (index !== -1) {
            availableDatabases.value.splice(index, 1)
          }
          
          // 如果当前选中的是被删除的数据库，切换到默认数据库
          if (selectedDatabase.value === databaseToDelete.value.name) {
            selectedDatabase.value = 'chembl33'
            switchDatabase()
          }
          
          // 关闭对话框
          cancelDeleteDatabase()
          
          // 在后台刷新数据库列表
          loadAvailableDatabases().catch(err => {
            console.warn('后台刷新数据库列表失败:', err)
          })
        } else {
          ElMessage.error(response.data.error || '删除数据库失败')
        }
        
      } catch (error) {
        console.error('删除数据库失败:', error)
        const errorMsg = error.response?.data?.error || error.message
        ElMessage.error('删除数据库失败: ' + errorMsg)
      } finally {
        deletingDatabase.value = false
             }
     }
     
     // =============================================================================
     // 表设计器相关方法
     // =============================================================================
     
     // 检查是否为用户数据库
     const isUserDatabase = (databaseName) => {
       // 只排除真正的MySQL系统数据库，允许业务数据库（如chembl33、tcrd6124expr2）创建表
       const systemDatabases = ['information_schema', 'mysql', 'performance_schema', 'sys']
       return !systemDatabases.includes(databaseName.toLowerCase())
     }
     
     // 显示表设计器对话框
     const showCreateTableDialog = () => {
       // 重置表设计
       tableDesign.value = {
         tableName: '',
         tableComment: '',
         columns: []
       }
       generatedSQL.value = ''
       
       // 加载数据类型
       loadDataTypes()
       
       // 显示对话框
       createTableDialogVisible.value = true
     }
     
     // 加载数据类型列表
     const loadDataTypes = async () => {
       try {
         const response = await api.get('/api/database/data-types')
         availableDataTypes.value = response.data
       } catch (error) {
         console.error('加载数据类型失败:', error)
         ElMessage.error('加载数据类型失败: ' + error.message)
       }
     }
     
     // 按类别分组的数据类型
     const groupedDataTypes = computed(() => {
       const groups = {}
       availableDataTypes.value.forEach(type => {
         if (!groups[type.category]) {
           groups[type.category] = {
             category: type.category,
             types: []
           }
         }
         groups[type.category].types.push(type)
       })
       return Object.values(groups)
     })
     
     // 检查是否有任何外键列
     const hasAnyForeignKey = computed(() => {
       return tableDesign.value.columns.some(col => col.isForeignKey)
     })
     
     // 添加新列
     const addColumn = () => {
       const newColumn = {
         name: '',
         dataType: 'VARCHAR',
         length: 255,
         decimals: null,
         isPrimary: false,
         isNotNull: false,
         isAutoIncrement: false,
         defaultValue: '',
         comment: '',
         nameError: '',
         // 外键相关字段
         isForeignKey: false,
         referenceTable: '',
         referenceColumn: '',
         onUpdate: 'RESTRICT',
         onDelete: 'CASCADE'
       }
       
       // 如果是第一列，默认设为主键和自增
       if (tableDesign.value.columns.length === 0) {
         newColumn.name = 'id'
         newColumn.dataType = 'INT'
         newColumn.length = 11
         newColumn.isPrimary = true
         newColumn.isNotNull = true
         newColumn.isAutoIncrement = true
         newColumn.comment = '主键ID'
       }
       
       tableDesign.value.columns.push(newColumn)
       updateSQL()
     }
     
     // 删除列
     const removeColumn = (index) => {
       tableDesign.value.columns.splice(index, 1)
       updateSQL()
     }
     
     // 移动列位置
     const moveColumnUp = (index) => {
       if (index > 0) {
         const temp = tableDesign.value.columns[index]
         tableDesign.value.columns[index] = tableDesign.value.columns[index - 1]
         tableDesign.value.columns[index - 1] = temp
         updateSQL()
       }
     }
     
     const moveColumnDown = (index) => {
       if (index < tableDesign.value.columns.length - 1) {
         const temp = tableDesign.value.columns[index]
         tableDesign.value.columns[index] = tableDesign.value.columns[index + 1]
         tableDesign.value.columns[index + 1] = temp
         updateSQL()
       }
     }
     
     // 验证列名
     const validateColumnName = (column) => {
       const name = column.name.trim()
       
       if (!name) {
         column.nameError = '列名不能为空'
         return false
       }
       
       if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(name)) {
         column.nameError = '列名只能包含字母、数字、下划线，不能以数字开头'
         return false
       }
       
       if (name.length > 64) {
         column.nameError = '列名长度不能超过64个字符'
         return false
       }
       
       // 检查重复列名
       const duplicateCount = tableDesign.value.columns.filter(col => col.name.trim() === name).length
       if (duplicateCount > 1) {
         column.nameError = '列名不能重复'
         return false
       }
       
       column.nameError = ''
       updateSQL()
       return true
     }
     
     // 数据类型改变时的处理
     const onDataTypeChange = (column) => {
       const dataType = availableDataTypes.value.find(type => type.name === column.dataType)
       if (dataType) {
         // 设置默认长度
         if (dataType.needsLength) {
           if (column.dataType === 'VARCHAR') {
             column.length = 255
           } else if (column.dataType === 'INT') {
             column.length = 11
           } else if (column.dataType === 'DECIMAL') {
             column.length = 10
             column.decimals = 2
           }
         } else {
           column.length = null
           column.decimals = null
         }
         
         // 自增只能用于整数类型
         if (!canAutoIncrement(column)) {
           column.isAutoIncrement = false
         }
       }
       updateSQL()
     }
     
     // 主键改变时的处理
     const onPrimaryKeyChange = (column) => {
       if (column.isPrimary) {
         // 设为主键时，自动设为非空
         column.isNotNull = true
         // 取消其他列的主键
         tableDesign.value.columns.forEach(col => {
           if (col !== column) {
             col.isPrimary = false
           }
         })
       }
       updateSQL()
     }
     
     // 检查数据类型是否需要长度
     const needsLength = (dataType) => {
       const type = availableDataTypes.value.find(t => t.name === dataType)
       return type ? type.needsLength : false
     }
     
     // 检查数据类型是否需要小数位
     const needsDecimals = (dataType) => {
       const type = availableDataTypes.value.find(t => t.name === dataType)
       return type ? type.needsDecimals : false
     }
     
     // 检查列是否可以设置自增
     const canAutoIncrement = (column) => {
       const integerTypes = ['TINYINT', 'SMALLINT', 'MEDIUMINT', 'INT', 'BIGINT']
       return integerTypes.includes(column.dataType) && column.isPrimary
     }
     
     // 外键改变时的处理
     const onForeignKeyChange = (column) => {
       if (!column.isForeignKey) {
         // 取消外键时清空相关字段
         column.referenceTable = ''
         column.referenceColumn = ''
         column.onUpdate = 'RESTRICT'
         column.onDelete = 'CASCADE'
       } else {
         // 设为外键时，加载可用表列表
         loadAvailableTables()
       }
       updateSQL()
     }
     
     // 引用表改变时的处理
     const onReferenceTableChange = (column) => {
       // 清空引用列
       column.referenceColumn = ''
       
       // 加载引用表的列
       if (column.referenceTable && column.referenceTable.trim()) {
         loadTableColumns(column.referenceTable)
       }
       
       updateSQL()
     }
     
     // 引用列改变时的处理
     const onReferenceColumnChange = (column) => {
       // 检查数据类型匹配
       if (column.referenceTable && column.referenceColumn) {
         validateDataTypeMatch(column)
       }
       updateSQL()
     }
     
     // 验证数据类型匹配
     const validateDataTypeMatch = (column) => {
       if (!column.referenceTable || !column.referenceColumn) return
       
       const referencedColumns = getTableColumns(column.referenceTable)
       const referencedColumn = referencedColumns.find(col => col.name === column.referenceColumn)
       
       if (referencedColumn) {
         const currentType = column.dataType.toUpperCase()
         const refType = referencedColumn.type.toUpperCase()
         
         // 简单的数据类型匹配检查
         const isCompatible = checkDataTypeCompatibility(currentType, refType, column.length)
         
         if (!isCompatible) {
           ElMessage.warning({
             message: `数据类型可能不匹配：当前列类型为 ${currentType}，引用列类型为 ${refType}。建议保持数据类型一致以避免创建失败。`,
             duration: 5000
           })
         }
       }
     }
     
     // 检查数据类型兼容性
     const checkDataTypeCompatibility = (currentType, refType, currentLength) => {
       // 标准化数据类型以便比较
       const normalizeType = (type) => {
         if (!type) return ''
         
         const upperType = type.toUpperCase()
         
         // 对于数值类型，移除显示宽度 (如 INT(11) -> INT)
         const numericTypes = ['TINYINT', 'SMALLINT', 'MEDIUMINT', 'INT', 'BIGINT']
         for (const numType of numericTypes) {
           if (upperType.startsWith(numType)) {
             return numType
           }
         }
         
         // 对于字符类型，保留但标准化格式
         if (upperType.startsWith('VARCHAR')) {
           return 'VARCHAR'
         }
         if (upperType.startsWith('CHAR')) {
           return 'CHAR'
         }
         
         // 移除括号中的内容获取基础类型
         const baseType = upperType.split('(')[0]
         return baseType
       }
       
       const normalizedCurrent = normalizeType(currentType)
       const normalizedRef = normalizeType(refType)
       
       // 标准化后的类型匹配
       if (normalizedCurrent === normalizedRef) return true
       
       // INT 系列的兼容性检查（虽然不建议混用）
       const intTypes = ['TINYINT', 'SMALLINT', 'MEDIUMINT', 'INT', 'BIGINT']
       if (intTypes.includes(normalizedCurrent) && intTypes.includes(normalizedRef)) {
         return true // 数值类型之间兼容，但建议保持一致
       }
       
       return false // 其他情况需要完全匹配
     }
     
     // 获取引用表的列列表
     const getTableColumns = (tableName) => {
       if (!tableName) {
         return []
       }
       
                const columns = referencedTableColumns.value[tableName]
         if (!columns || !Array.isArray(columns)) {
           return []
         }
         
         return columns
     }
     
     // 加载可用表列表
     const loadAvailableTables = async () => {
       try {
         const userInfo = userState.getUserInfo()
         // 使用专门的API函数
         const response = await databaseApi.getAllTables(
           selectedDatabase.value,
           userInfo.userId,
           userInfo.userType
         )
         
         if (response.data && Array.isArray(response.data)) {
           // 提取表名，支持多种数据格式
           availableTables.value = response.data.map(table => {
             if (typeof table === 'string') {
               return table
             } else if (table && typeof table === 'object') {
               return table.TABLE_NAME || table.tableName || table.name || ''
             }
             return ''
           }).filter(name => name.trim()) // 过滤空字符串
         }
       } catch (error) {
         console.error('加载表列表失败:', error)
         availableTables.value = []
       }
     }
     
          // 加载指定表的列信息
     const loadTableColumns = async (tableName) => {
       try {
         const userInfo = userState.getUserInfo()
         // 使用专门的API函数
         const response = await databaseApi.getTableColumns(
           tableName,
           selectedDatabase.value,
           userInfo.userId,
           userInfo.userType
         )
         
         if (response.data && Array.isArray(response.data)) {
           // 处理返回的列数据
           referencedTableColumns.value[tableName] = response.data.map(col => ({
             name: col.COLUMN_NAME || col.name,
             type: col.DATA_TYPE || col.type,
             label: `${col.COLUMN_NAME || col.name} (${col.DATA_TYPE || col.type})`
           }))
         } else {
           referencedTableColumns.value[tableName] = []
         }
       } catch (error) {
         console.error(`加载表 ${tableName} 的列信息失败:`, error)
         referencedTableColumns.value[tableName] = []
       }
     }
     
     // 更新SQL预览
     const updateSQL = () => {
       if (!canGenerateSQL.value) {
         generatedSQL.value = '-- 请填写表名和至少一个列'
         return
       }
       
       try {
         generatedSQL.value = generateCreateTableSQL()
       } catch (error) {
         generatedSQL.value = '-- SQL生成错误: ' + error.message
       }
     }
     
     // 生成CREATE TABLE SQL
     const generateCreateTableSQL = () => {
       const tableName = tableDesign.value.tableName.trim()
       const database = selectedDatabase.value
       const comment = tableDesign.value.tableComment.trim()
       
       let sql = `CREATE TABLE \`${database}\`.\`${tableName}\` (\n`
       
       // 列定义
       const columnDefs = []
       const primaryKeys = []
       
       tableDesign.value.columns.forEach(column => {
         if (!column.name.trim() || !column.dataType) return
         
         let columnDef = `  \`${column.name}\` ${column.dataType.toUpperCase()}`
         
         // 长度和精度
         if (needsLength(column.dataType) && column.length) {
           columnDef += `(${column.length}`
           if (needsDecimals(column.dataType) && column.decimals) {
             columnDef += `,${column.decimals}`
           }
           columnDef += ')'
         }
         
         // NOT NULL
         if (column.isNotNull) {
           columnDef += ' NOT NULL'
         }
         
         // AUTO_INCREMENT
         if (column.isAutoIncrement) {
           columnDef += ' AUTO_INCREMENT'
         }
         
         // 默认值
         if (column.defaultValue && column.defaultValue.trim() && !column.isAutoIncrement) {
           const defaultValue = column.defaultValue.trim()
           const stringTypes = ['CHAR', 'VARCHAR', 'TEXT', 'TINYTEXT', 'MEDIUMTEXT', 'LONGTEXT', 'JSON', 'ENUM', 'SET']
           if (stringTypes.includes(column.dataType.toUpperCase())) {
             columnDef += ` DEFAULT '${defaultValue.replace(/'/g, "\\'")}'`
           } else {
             columnDef += ` DEFAULT ${defaultValue}`
           }
         }
         
         // 注释
         if (column.comment && column.comment.trim()) {
           columnDef += ` COMMENT '${column.comment.trim().replace(/'/g, "\\'")}'`
         }
         
         columnDefs.push(columnDef)
         
         // 收集主键
         if (column.isPrimary) {
           primaryKeys.push(column.name)
         }
       })
       
       sql += columnDefs.join(',\n')
       
       // 主键约束
       if (primaryKeys.length > 0) {
         sql += ',\n  PRIMARY KEY (' + primaryKeys.map(key => `\`${key}\``).join(', ') + ')'
       }
       
       // 外键约束
       const foreignKeys = []
       tableDesign.value.columns.forEach(column => {
         if (column.isForeignKey && column.referenceTable && column.referenceColumn) {
           const constraintName = `fk_${tableName}_${column.name}`
           let foreignKeyDef = `  CONSTRAINT \`${constraintName}\` FOREIGN KEY (\`${column.name}\`) `
           foreignKeyDef += `REFERENCES \`${database}\`.\`${column.referenceTable}\` (\`${column.referenceColumn}\`)`
           
           if (column.onUpdate && column.onUpdate !== 'RESTRICT') {
             foreignKeyDef += ` ON UPDATE ${column.onUpdate}`
           }
           if (column.onDelete && column.onDelete !== 'RESTRICT') {
             foreignKeyDef += ` ON DELETE ${column.onDelete}`
           }
           
           foreignKeys.push(foreignKeyDef)
         }
       })
       
       if (foreignKeys.length > 0) {
         sql += ',\n' + foreignKeys.join(',\n')
       }
       
       sql += '\n) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci'
       
       // 表注释
       if (comment) {
         sql += ` COMMENT='${comment.replace(/'/g, "\\'")}'`
       }
       
       sql += ';'
       
       return sql
     }
     
     // 预览SQL
     const previewSQL = () => {
       updateSQL()
       ElMessage.success('SQL已更新')
     }
     
     // 检查是否可以生成SQL
     const canGenerateSQL = computed(() => {
       return tableDesign.value.tableName.trim() && 
              tableDesign.value.columns.length > 0 &&
              tableDesign.value.columns.some(col => col.name.trim() && col.dataType)
     })
     
     // 检查是否可以创建表
     const canCreateTable = computed(() => {
       if (!canGenerateSQL.value) return false
       
       // 检查表名是否有效
       const tableName = tableDesign.value.tableName.trim()
       if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(tableName)) return false
       
       // 检查所有列名是否有效且无重复
       const columnNames = new Set()
       for (const column of tableDesign.value.columns) {
         const name = column.name.trim()
         if (!name || !/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(name)) return false
         if (columnNames.has(name)) return false
         columnNames.add(name)
       }
       
       return true
     })
     
     // 确认创建表
     const confirmCreateTable = async () => {
       if (!canCreateTable.value) {
         ElMessage.error('表设计不完整，请检查')
         return
       }
       
       try {
         // 表单验证
         if (tableFormRef.value) {
           const valid = await tableFormRef.value.validate()
           if (!valid) return
         }
         
         creatingTable.value = true
         
         const userInfo = userState.getUserInfo()
         const requestData = {
           tableName: tableDesign.value.tableName.trim(),
           tableComment: tableDesign.value.tableComment.trim(),
           databaseName: selectedDatabase.value,
           dataSource: 'chembl33',
           columns: tableDesign.value.columns.map(col => ({
             name: col.name.trim(),
             dataType: col.dataType,
             length: col.length,
             decimals: col.decimals,
             isPrimary: col.isPrimary,
             isNotNull: col.isNotNull,
             isAutoIncrement: col.isAutoIncrement,
             defaultValue: col.defaultValue ? col.defaultValue.trim() : null,
             comment: col.comment ? col.comment.trim() : null,
             // 外键相关字段
             isForeignKey: col.isForeignKey,
             referenceTable: col.referenceTable || null,
             referenceColumn: col.referenceColumn || null,
             onUpdate: col.onUpdate || 'RESTRICT',
             onDelete: col.onDelete || 'CASCADE'
           })).filter(col => col.name && col.dataType),
           userId: userInfo.userId,
           userType: userInfo.userType
         }
         
         const response = await api.post('/api/database/tables/create', requestData)
         
         if (response.data.success) {
           ElMessage.success(response.data.message)
           createTableDialogVisible.value = false
           
           // 刷新表列表
           await loadTables(false)
         } else {
           ElMessage.error(response.data.error || '创建表失败')
         }
         
       } catch (error) {
         console.error('创建表失败:', error)
         const errorMsg = error.response?.data?.error || error.message
         
         // 提供更友好的错误信息
         let friendlyMessage = '创建表失败'
         
         if (errorMsg) {
           if (errorMsg.includes('外键约束创建失败') || errorMsg.includes('数据类型') || errorMsg.includes('不匹配')) {
             friendlyMessage = '外键设置有误：' + errorMsg
           } else if (errorMsg.includes('表已存在')) {
             friendlyMessage = '表名已被使用，请更换其他表名'
           } else if (errorMsg.includes('列名重复')) {
             friendlyMessage = '存在重复的列名，请检查并修改'
           } else if (errorMsg.includes('权限')) {
             friendlyMessage = '权限不足，无法创建表'
           } else if (errorMsg.includes('不存在')) {
             friendlyMessage = '引用的表或列不存在，请检查外键设置'
           } else if (errorMsg.includes('超限') || errorMsg.includes('too long')) {
             friendlyMessage = '字段长度设置过大，请适当调整'
           } else {
             friendlyMessage = '创建表失败：' + errorMsg
           }
         }
         
         ElMessage.error(friendlyMessage)
       } finally {
         creatingTable.value = false
       }
     }
     
     // 取消创建表
     const cancelCreateTable = () => {
       createTableDialogVisible.value = false
       tableDesign.value = {
         tableName: '',
         tableComment: '',
         columns: []
       }
       generatedSQL.value = ''
     }
      
      // 左侧数据库选择方法
      const selectDatabase = (databaseName) => {
        selectedDatabase.value = databaseName
        switchDatabase()
      }
      
      // 获取当前选中数据库的信息
          const getSelectedDatabaseInfo = () => {
      return availableDatabases.value.find(db => db.name === selectedDatabase.value)
    }

    // 为不同数据库分配颜色
    const getDatabaseColor = (dataSource) => {
      if (!dataSource) return '#dcdfe6'
      
      const colors = [
        '#409eff', // 蓝色
        '#67c23a', // 绿色  
        '#e6a23c', // 橙色
        '#f56c6c', // 红色
        '#909399', // 灰色
        '#9b59b6', // 紫色
        '#17a2b8', // 青色
        '#fd7e14'  // 深橙色
      ]
      
      // 根据数据库名称生成一个稳定的颜色索引
      let hash = 0
      for (let i = 0; i < dataSource.length; i++) {
        hash = ((hash << 5) - hash) + dataSource.charCodeAt(i)
        hash = hash & hash // 转换为32位整数
      }
      
      return colors[Math.abs(hash) % colors.length]
    }
      
      // 动态计算表格最大高度
      const tableMaxHeight = ref(450)
      
      const getTableMaxHeight = () => {
        return tableMaxHeight.value
      }
      
      const updateTableHeight = () => {
        // 基于视口高度计算，减去头部、导航、卡片标题等高度
        const baseHeight = window.innerHeight - 380 // 380px是其他元素的大概高度
        tableMaxHeight.value = Math.max(baseHeight, 300) // 最小高度300px
      }

      const loadDatabaseInfo = async () => {
        try {
          console.log('开始加载数据库信息:', selectedDatabase.value)
          const response = await api.get(`/api/database/info?database=${selectedDatabase.value}`)
          databaseInfo.value = {
            name: selectedDatabase.value,
            tableCount: response.data.tableCount || 0,
            sizeDisplay: formatBytes(response.data.totalSize || 0)
          }
          console.log('数据库信息加载成功:', databaseInfo.value)
        } catch (error) {
          console.error('加载数据库信息失败:', error)
          // 设置默认值，使用当前的表列表长度作为表数量
          databaseInfo.value = {
            name: selectedDatabase.value,
            tableCount: tableList.value?.length || 0,
            sizeDisplay: 'N/A'
          }
          console.log('使用默认数据库信息:', databaseInfo.value)
        }
      }

      const loadAvailableDatabases = async () => {
        try {
          const userInfo = userState.getUserInfo()
          
          // 尝试获取所有数据库（包括用户创建的）
          const response = await api.get('/api/database/all', {
            params: {
              dataSource: 'chembl33',
              userId: userInfo.userId,
              userType: userInfo.userType
            }
          })
          availableDatabases.value = response.data
        } catch (error) {
          console.error('加载完整数据库列表失败，尝试获取默认列表:', error)
          
          // 如果获取完整列表失败，尝试获取默认数据库列表
          try {
            const fallbackResponse = await api.get('/api/database/databases')
            availableDatabases.value = fallbackResponse.data
          } catch (fallbackError) {
            console.error('加载默认数据库列表也失败:', fallbackError)
            // 如果都失败，使用硬编码的默认数据库
          availableDatabases.value = [
            { name: 'chembl33', displayName: 'ChEMBL 33', description: 'ChEMBL化学生物学数据库' },
            { name: 'tcrd6124expr2', displayName: 'TCRD 6.12.4', description: '目标中心研究数据库' },
            { name: 'login', displayName: 'Login Database', description: '用户登录数据库（仅管理员）' }
          ]
          }
        }
      }

      // =============================================================================
      // 删除表相关方法
      // =============================================================================
      
      // 确认删除表
      const confirmDeleteTable = async (tableName) => {
        try {
          const userInfo = userState.getUserInfo()
          
          // 权限检查
          if (!canModifyCurrentDatabase.value) {
            ElMessage.error('您没有权限删除表')
            return
          }
          
          // 确认对话框
          const result = await ElMessageBox.confirm(
            `确定要删除表 "${tableName}" 吗？\n\n警告：此操作将永久删除表及其所有数据，且无法恢复！`,
            '确认删除表',
            {
              confirmButtonText: '确定删除',
              cancelButtonText: '取消',
              type: 'warning',
              dangerouslyUseHTMLString: false
            }
          )

          if (result === 'confirm') {
            await deleteTable(tableName)
          }
        } catch (error) {
          // 用户取消删除，不需要处理
          if (error !== 'cancel') {
            console.error('删除表确认出错:', error)
          }
        }
      }

      // 删除表
      const deleteTable = async (tableName) => {
        try {
          const userInfo = userState.getUserInfo()
          
          const requestData = {
            dataSource: 'chembl33',
            databaseName: selectedDatabase.value,
            tableName: tableName,
            userId: userInfo.userId,
            userType: userInfo.userType
          }
          
          const response = await api.delete('/api/database/tables/drop', {
            data: requestData
          })
          
          if (response.data.success) {
            ElMessage.success(`表 "${tableName}" 删除成功`)
            
            // 刷新表列表
            await loadTables(false)
          } else {
            ElMessage.error(response.data.error || '删除表失败')
          }
          
        } catch (error) {
          console.error('删除表失败:', error)
          const errorMsg = error.response?.data?.error || error.message
          ElMessage.error('删除表失败: ' + errorMsg)
        }
      }

      // 计算属性：过滤后的表列表
      const filteredTableList = computed(() => {
        if (!searchQuery.value) {
          return tableList.value
        }
        return tableList.value.filter(table => 
          table.TABLE_NAME.toLowerCase().includes(searchQuery.value.toLowerCase())
        )
      })

      onMounted(() => {
        // 初始化权限状态
        updatePermissionState()
        loadAvailableDatabases()
        
        // 初始化表格高度
        updateTableHeight()
        
        // 监听窗口大小变化
        window.addEventListener('resize', updateTableHeight)
        
        // 只有在用户已登录时才加载数据
        const userInfo = userState.getUserInfo()
        if (userInfo.userId && userInfo.userType) {
          loadTables(false)
          loadDatabaseInfo()
        }
      })
      
      onUnmounted(() => {
        // 清理事件监听器
        window.removeEventListener('resize', updateTableHeight)
      })

    return {
      tableList,
      selectedTable,
      structureDialogVisible,
      dataDialogVisible,
      addDataDialogVisible,
      editDataDialogVisible,
      tableColumns,
      tableData,
      dataLimit,
      newRowData,
      editRowData,
      originalRowData,
      submitting,
      hasPrimaryKey,
      tableDataLoading,
      pagination,
      loadTables,
      selectTable,
      viewTableStructure,
      viewTableData,
      loadTableData,
      loadTableDataWithPagination,
      loadSearchResultDataWithPagination,
      handleSizeChange,
      handleCurrentChange,
      changePageSize,
      showAddDataDialog,
      submitNewData,
      editRow,
      submitEditData,
      getColumnPlaceholder,
      getColumnTagType,
      getColumnDescription,
      confirmDeleteRow,
      deleteRow,
      formatBytes,
      formatDate,
      getColumnWidth,
      Delete,
      Edit,
      searchQuery,
      loadingTables,
      databaseInfo,
      selectedDatabase,
      availableDatabases,
      switchDatabase,
      selectDatabase,
      getSelectedDatabaseInfo,
      getTableMaxHeight,
      tableMaxHeight,
      updateTableHeight,
      filteredTableList,
      loadAvailableDatabases,
      // 新建数据库相关
      createDatabaseDialogVisible,
      creatingDatabase,
      databaseFormRef,
      newDatabaseForm,
      databaseRules,
      showCreateDatabaseDialog,
      createDatabase,
      // 删除数据库相关
      deleteDatabaseDialogVisible,
      deletingDatabase,
      databaseToDelete,
      deleteConfirmText,
      canDeleteDatabase,
      showDeleteDatabaseDialog,
      cancelDeleteDatabase,
      confirmDeleteDatabase,
      // 表设计器相关
      createTableDialogVisible,
      creatingTable,
      tableFormRef,
      tableDesign,
      tableRules,
      availableDataTypes,
      groupedDataTypes,
      generatedSQL,
      isUserDatabase,
      showCreateTableDialog,
      addColumn,
      removeColumn,
      moveColumnUp,
      moveColumnDown,
      validateColumnName,
      onDataTypeChange,
      onPrimaryKeyChange,
      needsLength,
      needsDecimals,
      canAutoIncrement,
      updateSQL,
      previewSQL,
      canGenerateSQL,
      canCreateTable,
      confirmCreateTable,
      // 外键相关
      hasAnyForeignKey,
      availableTables,
      referencedTableColumns,
      onForeignKeyChange,
      onReferenceTableChange,
      onReferenceColumnChange,
      getTableColumns,
      loadAvailableTables,
      loadTableColumns,
      validateDataTypeMatch,
      checkDataTypeCompatibility,
      cancelCreateTable,
      // 权限相关
      canAccessCurrentDatabase,
      canModifyCurrentDatabase,
      canCreateDatabase,
      permissionMessage,
      checkDatabasePermission,
      updatePermissionState,
      // 删除表相关
      confirmDeleteTable,
      deleteTable,
      // 字段搜索相关
      fieldSearchQuery,
      searchMode,
      fieldSearching,
      fieldSearchResult,
      fieldSearchError,
      fieldSearchResultDialogVisible,
      searchTablesByFieldValue,
      viewSearchResultData,
      clearFieldSearch,
      cellContainsSearchValue,
      // 多弹窗管理
      searchDialogs,
      createNewSearchDialog,
      minimizeDialog,
      restoreDialog,
      closeDialog,
      clearSingleSearch,
      clearAllSearches,
      clearSearchesByDatabase,
      // 搜索进度相关
      searchProgressDialogVisible,
      currentSearchValue,
      searchProgressPercentage,
      searchProgressText,
      currentSearchingTable,
      foundTablesCount,
      totalTablesCount,
      searchElapsedTime,
      cancelling,
      showSearchProgressDialog,
      closeSearchProgressDialog,
      formatElapsedTime,
      cancelSearch,
      getSelectedDatabaseInfo,
      getDatabaseColor
    }
  }
}
</script>

<style scoped>
.database-view {
  padding: 20px 15px 20px 10px;
  height: calc(100vh - 120px);
}

.database-layout {
  display: flex;
  height: 100%;
  gap: 25px;
}

/* 左侧数据库导航栏 */
.database-sidebar {
  width: 240px;
  flex-shrink: 0;
}

.sidebar-card {
  height: 100%;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  color: #303133;
}

.sidebar-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.database-list {
  max-height: calc(100vh - 250px);
  overflow-y: auto;
  padding-bottom: 8px;
}

.database-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  margin-bottom: 8px;
  border-radius: 6px;
  transition: all 0.2s;
  border: 1px solid transparent;
  position: relative;
}

.database-item:last-child {
  margin-bottom: 0;
}

.database-item:hover {
  background-color: #f5f7fa;
  border-color: #e4e7ed;
}

.database-item.active {
  background-color: #ecf5ff;
  border-color: #409eff;
  color: #409eff;
}

.database-content {
  display: flex;
  align-items: center;
  flex: 1;
  cursor: pointer;
  min-width: 0;
}

.database-actions {
  display: flex;
  align-items: center;
  opacity: 0;
  transition: opacity 0.2s;
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
}

.database-item:hover .database-actions {
  opacity: 1;
}

.database-icon {
  margin-right: 12px;
  font-size: 18px;
  color: #909399;
}

.card-title {
  display: flex;
  align-items: center;
}

/* 表设计器样式 */
.table-designer {
  max-height: 70vh;
  overflow-y: auto;
}

.basic-info-card,
.columns-card,
.sql-preview-card {
  border: 1px solid #e4e7ed;
}

.empty-columns {
  text-align: center;
  padding: 40px 0;
}

.columns-editor {
  max-height: 400px;
  overflow-y: auto;
}

.database-item.active .database-icon {
  color: #409eff;
}

.database-info {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.database-name {
  font-weight: 500;
  font-size: 14px;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: center;
}

.database-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: center;
}

.database-item.active .database-desc {
  color: #73b3ff;
}

/* 右侧内容区域 */
.database-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 15px;
  min-width: 0;
  overflow: hidden;
  width: 100%;
  max-width: none;
}

.database-content-layout {
  display: flex;
  flex-direction: column;
  gap: 15px;
  height: 100%;
  overflow: hidden;
  width: 100%;
  max-width: none;
}

/* 数据库信息卡片 */
.database-info-card {
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  width: 100%;
  max-width: none;
  flex-shrink: 0;
}

.database-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #303133;
}

.tables-card {
  flex: 1;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  width: 100%;
  max-width: none;
}

.tables-card .el-card__body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
  max-height: calc(100vh - 320px);
  width: 100%;
  max-width: none;
}

.tables-card .el-table {
  flex: 1;
  width: 100% !important;
}

.el-table {
  width: 100% !important;
}

.el-row {
  width: 100%;
}

.el-col {
  width: 100%;
}

.table-container {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: none;
}

.table-container .el-table {
  flex: 1;
  width: 100%;
}

.welcome-card {
  flex: 1;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: none;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  max-width: none;
}

.el-table .el-table__row {
  cursor: pointer;
}

.el-table .el-table__row:hover {
  background-color: #f5f7fa;
}

.table-actions {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.action-btn {
  margin: 0 !important;
  min-width: 60px;
  height: 28px;
  font-size: 12px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.action-btn .el-icon {
  margin-right: 4px;
}

/* 自定义滚动条样式 */
.database-list::-webkit-scrollbar,
.tables-card .el-card__body::-webkit-scrollbar,
.el-table__body-wrapper::-webkit-scrollbar {
  width: 6px;
}

/* 数据库导航栏默认隐藏滚动条 */
.database-list::-webkit-scrollbar-track {
  background: transparent;
  border-radius: 3px;
}

.database-list::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 3px;
}

/* 鼠标悬停时显示数据库导航栏滚动条 */
.database-list:hover::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.database-list:hover::-webkit-scrollbar-thumb {
  background: #c1c1c1;
}

.database-list:hover::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 其他区域的滚动条样式保持不变 */
.tables-card .el-card__body::-webkit-scrollbar-track,
.el-table__body-wrapper::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.tables-card .el-card__body::-webkit-scrollbar-thumb,
.el-table__body-wrapper::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.tables-card .el-card__body::-webkit-scrollbar-thumb:hover,
.el-table__body-wrapper::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 确保滚动条不会遮挡内容 */
.database-list {
  scrollbar-gutter: stable;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .database-sidebar {
    width: 210px;
  }
  
  .database-info-card {
    width: 100%;
    max-width: none;
  }
  
  .database-name {
    font-size: 13px;
  }
  
  .database-desc {
    font-size: 11px;
  }
}

@media (max-width: 900px) {
  .database-layout {
    flex-direction: column;
    height: auto;
    gap: 10px;
  }
  
  .database-sidebar {
    width: 100%;
    height: auto;
  }
  
  .database-list {
    max-height: 300px;
    padding-bottom: 8px;
  }
  
  .database-info-card {
    width: 100%;
    max-width: none;
  }
  
  .tables-card {
    width: 100%;
    max-width: none;
  }
  
  .welcome-card {
    width: 100%;
    max-width: none;
  }
  
  .database-view {
    height: auto;
  }
}

.minimized-dialog {
  position: fixed;
  background-color: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.3s ease;
  z-index: 2000;
  min-width: 250px;
  max-width: 350px;
}

.minimized-dialog:hover {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  transform: translateY(-2px);
}

.minimized-content {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.search-value {
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100px;
}

.database-info {
  font-size: 11px;
  color: #909399;
  background-color: #f4f4f5;
  padding: 2px 4px;
  border-radius: 4px;
  white-space: nowrap;
  margin: 0 2px;
}

.match-count {
  font-size: 12px;
  color: #fff;
  background-color: #e6a23c;
  padding: 2px 6px;
  border-radius: 10px;
  font-weight: 500;
  white-space: nowrap;
}

.close-btn {
  margin-left: 8px;
  flex-shrink: 0;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.dialog-header h4 {
  margin: 0;
  font-size: 16px;
  color: #303133;
  flex: 1;
}

.dialog-controls {
  display: flex;
  gap: 8px;
  align-items: center;
}

.search-progress-content {
  padding: 20px;
  text-align: center;
}

.progress-header {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}

.spinning-icon {
  margin-right: 10px;
}

.progress-title {
  font-size: 16px;
  font-weight: 600;
}

.progress-info {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.search-info {
  margin-bottom: 15px;
}

.progress-details {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.progress-text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.current-progress {
  font-size: 14px;
  color: #303133;
}

.time-info {
  font-size: 12px;
  color: #909399;
}

.current-table {
  margin-top: 10px;
  text-align: left;
}

.found-info {
  margin-top: 10px;
  text-align: left;
}

.cancel-search {
  margin-top: 15px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.spinning-icon {
  animation: spin 2s linear infinite;
  color: #409eff;
  font-size: 20px;
}
</style> 