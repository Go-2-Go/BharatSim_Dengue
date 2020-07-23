const mongoose = require('mongoose');
const fs = require('fs');

const dataSourceMetadataRepository = require('../../src/repository/datasourceMetadataRepository');
const dataSourceRepository = require('../../src/repository/datasourceRepository');
const uploadDatasourceService = require('../../src/services/uploadDatasourceService');
const createModel = require('../../src/utils/modelCreator');

const InvalidInputException = require('../../src/exceptions/InvalidInputException');

jest.mock('fs');
jest.mock('../../src/repository/datasourceMetadataRepository');
jest.mock('../../src/repository/datasourceRepository');
jest.mock('../../src/utils/modelCreator');
jest.mock('../../src/utils/csvParser', () => ({
  validateAndParseCSV: jest.fn().mockReturnValue([
    { hour: 0, susceptible: 1 },
    { hour: 1, susceptible: 2 },
    { hour: 2, susceptible: 3 },
  ]),
}));

describe('upload datasource  service', function () {
  describe('Uploaddatasource', function () {
    it('should insert schema and datasource name in dataSource metadata for uploaded csv', async function () {
      dataSourceMetadataRepository.insert.mockResolvedValue({ _id: 'collection' });

      const collectionId = await uploadDatasourceService.uploadCsv({
        path: '/uploads/1223',
        originalname: 'test.csv',
        mimetype: 'text/csv',
        size: 12132,
      });

      expect(collectionId).toEqual({ collectionId: 'collection' });
    });

    it('should insert schema and datasource name in dataSource metadata for uploaded csv', async function () {
      dataSourceMetadataRepository.insert.mockResolvedValue({ _id: new mongoose.Types.ObjectId(123123) });

      await uploadDatasourceService.uploadCsv({
        path: '/uploads/1223',
        originalname: 'test.csv',
        mimetype: 'text/csv',
        size: 12132,
      });

      expect(dataSourceMetadataRepository.insert).toHaveBeenCalledWith({
        dataSourceSchema: { hour: 'number', susceptible: 'number' },
        name: 'test.csv',
      });
    });

    it('should insert data in data source collection', async function () {
      dataSourceMetadataRepository.insert.mockResolvedValue({ _id: 'collectionId' });
      createModel.createModel.mockImplementation((id) => id);

      await uploadDatasourceService.uploadCsv({
        path: '/uploads/1223',
        originalname: 'test.csv',
        mimetype: 'text/csv',
        size: 12132,
      });

      expect(dataSourceRepository.insert).toHaveBeenCalledWith('collectionId', [
        { hour: 0, susceptible: 1 },
        { hour: 1, susceptible: 2 },
        { hour: 2, susceptible: 3 },
      ]);
    });

    it('should throw invalid input exception if we get exception while uploading', async function () {
      dataSourceMetadataRepository.insert.mockResolvedValue({ _id: 'collectionId' });
      dataSourceRepository.insert.mockImplementationOnce(() => {
        throw new Error();
      });
      createModel.createModel.mockImplementation((id) => id);

      const result = async () => {
        await uploadDatasourceService.uploadCsv({
          path: '/uploads/1223',
          originalname: 'test.csv',
          mimetype: 'text/csv',
          size: 12132,
        });
      };

      expect(result).rejects.toThrow(new InvalidInputException('Error while uploading csv file data'));
    });

    it('should delete metadata added in database if csv data insertion failed', async function () {
      dataSourceMetadataRepository.insert.mockResolvedValue({ _id: 'collectionId' });
      dataSourceRepository.insert.mockImplementationOnce(() => {
        throw new Error();
      });
      createModel.createModel.mockImplementation((id) => id);

      try {
        await uploadDatasourceService.uploadCsv({
          path: '/uploads/1223',
          originalname: 'test.csv',
          mimetype: 'text/csv',
          size: 12132,
        });
      } catch {
        expect(dataSourceMetadataRepository.deleteDatasource).toHaveBeenCalledWith('collectionId');
      }
    });

    it('should throw exception is file is too large', async function () {
      dataSourceMetadataRepository.insert.mockResolvedValue({ _id: 'collectionId' });
      dataSourceRepository.insert.mockImplementationOnce(() => {
        throw new Error();
      });
      createModel.createModel.mockImplementation((id) => id);

      const result = async () => {
        await uploadDatasourceService.uploadCsv({
          path: '/uploads/1223',
          originalname: 'test.csv',
          mimetype: 'text/csv',
          size: 10485761,
        });
      };

      expect(result).rejects.toThrow(new InvalidInputException('File is too large'));
    });
  });

  describe('deleteUploadedFile', function () {
    afterEach(() => {
      jest.clearAllMocks();
    });
    it('should delete file for given path', function () {
      fs.existsSync.mockReturnValue(true);

      uploadDatasourceService.deleteUploadedFile('path');

      expect(fs.rmdirSync).toHaveBeenCalledWith('path', { recursive: true });
    });

    it('should not delete file if given path not exist', function () {
      fs.existsSync.mockReturnValue(false);

      uploadDatasourceService.deleteUploadedFile('path');

      expect(fs.rmdirSync).not.toHaveBeenCalled();
    });
  });
});