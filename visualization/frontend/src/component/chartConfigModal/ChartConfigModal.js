import React from 'react';
import PropTypes from 'prop-types';

import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Typography,
  withStyles,
} from '@material-ui/core';
import Dropdown from '../../uiComponent/Dropdown';
import renderChartConfig from '../chartConfigOptions/renderChartConfig';
import styles from './chartConfigModalCss';

import useFetch from '../../hook/useFetch';
import useForm from '../../hook/useForm';
import { fetch } from '../../utils/fetch';
import { convertObjectArrayToOptionStructure } from '../../utils/helper';
import chartConfigs from '../../config/chartConfigs';
import { url } from '../../utils/url';
import { datasourceValidator } from '../../utils/validators';

function ChartConfigModal({ open, onCancel, onOk, chartType, classes }) {
  const [headers, setHeaders] = React.useState([]);
  const datasources = useFetch({ url: url.DATA_SOURCES });
  const {
    values,
    validateAndSetValue,
    errors,
    shouldEnableSubmit,
    onSubmit,
    resetFields,
  } = useForm({
    ...chartConfigs[chartType].configOptionValidationSchema,
    dataSource: datasourceValidator,
  });

  if (!datasources) {
    return null;
  }

  const handleDataSourceChange = async (value) => {
    resetFields(chartConfigs[chartType].configOptions);
    validateAndSetValue('dataSource', value);
    const csvHeaders = await fetch({ url: url.getHeaderUrl(value) });
    setHeaders(csvHeaders.headers);
  };

  const handleOk = () => {
    onSubmit((value) => {
      onOk(value);
    });
  };

  const chartConfigProps = { headers, updateConfigState: validateAndSetValue, errors, values };

  return (
    <Dialog open={open} onClose={onCancel} aria-labelledby="form-dialog-title">
      <DialogTitle id="form-dialog-title">Chart Config</DialogTitle>

      {datasources.dataSources.length === 0 ? (
        <Box p={10}>
          <Typography>No data source present, upload data source</Typography>
        </Box>
      ) : (
        <>
          <DialogContent>
            <Box className={classes.root}>
              <Dropdown
                options={convertObjectArrayToOptionStructure(
                  datasources.dataSources,
                  'name',
                  '_id',
                )}
                onChange={handleDataSourceChange}
                id="dropdown-dataSources"
                label="select data source"
                error={errors.dataSource || ''}
                value={values.dataSource || ''}
              />
              {!!headers.length &&
                renderChartConfig(chartConfigs[chartType].configOptions, chartConfigProps)}
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={onCancel} variant="contained" color="secondary">
              Cancel
            </Button>
            <Button
              onClick={handleOk}
              variant="contained"
              color="primary"
              data-testid="button-ok"
              disabled={!shouldEnableSubmit()}
            >
              Ok
            </Button>
          </DialogActions>
        </>
      )}
    </Dialog>
  );
}

ChartConfigModal.propTypes = {
  open: PropTypes.bool.isRequired,
  onCancel: PropTypes.func.isRequired,
  onOk: PropTypes.func.isRequired,
  chartType: PropTypes.string.isRequired,
  classes: PropTypes.shape({
    root: PropTypes.string,
  }).isRequired,
};

export default withStyles(styles)(ChartConfigModal);
export { ChartConfigModal };
