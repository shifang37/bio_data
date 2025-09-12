import ExcelJS from 'exceljs';

/**
 * 解析Excel文件
 * @param {File} file - Excel文件对象
 * @returns {Promise<Array>} 返回工作表数组
 */
export const parseExcelFile = (file) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    
    reader.onload = (e) => {
      try {
        const workbook = new ExcelJS.Workbook();
        const buffer = e.target.result;
        
        workbook.xlsx.load(buffer).then(() => {
          const sheets = [];
          
          workbook.eachSheet((worksheet, sheetId) => {
            const sheetData = [];
            const maxRow = worksheet.rowCount;
            const maxCol = worksheet.columnCount;
            
            // 如果工作表为空，跳过
            if (maxRow === 0 || maxCol === 0) {
              return;
            }
            
            for (let row = 1; row <= maxRow; row++) {
              const rowData = [];
              for (let col = 1; col <= maxCol; col++) {
                const cell = worksheet.getCell(row, col);
                // 处理不同类型的单元格值
                let cellValue = '';
                if (cell.value !== null && cell.value !== undefined) {
                  if (typeof cell.value === 'object' && cell.value.text) {
                    // 富文本单元格
                    cellValue = cell.value.text;
                  } else if (cell.value instanceof Date) {
                    // 日期单元格
                    cellValue = cell.value.toISOString().split('T')[0];
                  } else {
                    // 普通单元格
                    cellValue = String(cell.value);
                  }
                }
                rowData.push(cellValue);
              }
              sheetData.push(rowData);
            }
            
            sheets.push({
              name: worksheet.name,
              data: sheetData,
              rowCount: maxRow,
              columnCount: maxCol
            });
          });
          
          resolve(sheets);
        }).catch(reject);
        
      } catch (error) {
        reject(error);
      }
    };
    
    reader.onerror = () => reject(new Error('文件读取失败'));
    reader.readAsArrayBuffer(file);
  });
};

/**
 * 将Excel数据转换为CSV格式（与现有CSV解析结果保持一致）
 * @param {Array} excelData - Excel数据数组
 * @param {boolean} hasHeader - 是否包含标题行
 * @returns {Object} 返回{data, columns}格式的数据
 */
export const convertExcelToCsvFormat = (excelData, hasHeader = true) => {
  if (!excelData || excelData.length === 0) {
    return { data: [], columns: [] };
  }
  
  let data = excelData;
  let columns = [];
  
  if (hasHeader && data.length > 0) {
    // 使用第一行作为列名
    columns = data[0].map((col, index) => {
      // 确保列名不为空，如果为空则生成默认列名
      return col && String(col).trim() ? String(col).trim() : `Column_${index + 1}`;
    });
    data = data.slice(1);
  } else {
    // 如果没有标题行，生成默认列名
    columns = data[0] ? data[0].map((_, index) => `Column_${index + 1}`) : [];
  }
  
  // 转换为与CSV解析结果相同的格式
  const csvFormatData = data.map(row => {
    const obj = {};
    columns.forEach((col, index) => {
      obj[col] = row[index] || '';
    });
    return obj;
  });
  
  return {
    data: csvFormatData,
    columns: columns
  };
};

/**
 * 检测文件类型
 * @param {File} file - 文件对象
 * @returns {string} 返回文件类型 'excel' 或 'csv'
 */
export const detectFileType = (file) => {
  const fileExtension = file.name.split('.').pop().toLowerCase();
  if (['xlsx', 'xls'].includes(fileExtension)) {
    return 'excel';
  } else if (['csv'].includes(fileExtension)) {
    return 'csv';
  }
  return 'unknown';
};

/**
 * 获取支持的文件类型
 * @returns {Array} 返回支持的文件类型数组
 */
export const getSupportedFileTypes = () => {
  return [
    { name: 'CSV文件', extensions: ['.csv'], type: 'csv' },
    { name: 'Excel文件', extensions: ['.xlsx', '.xls'], type: 'excel' }
  ];
};
