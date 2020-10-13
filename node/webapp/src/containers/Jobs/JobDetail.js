import React, {Component, Fragment} from 'react';
import {Table, Row, PageHeader, Card, Button, Col, Input, Steps} from 'antd';
import xhr from "axios/index";
import {Tabs, Select, Space} from 'antd';
import $ from "jquery";
import {JsonFormat} from "../../utils/JsonFormat";
import {Modal} from "antd/lib/index";
import {CopyToClipboard} from "react-copy-to-clipboard";

const { Step } = Steps;
const {TabPane} = Tabs;

const API_URL = process.env.API_URL;

const status = {0:'init', 1:'processing', 2:'complete', 3:'error'};
const ant_status = {0: 'wait', 1: 'process', 2: 'finish', 3: 'error'};

const columns = [
  {
    title: 'Status',
    dataIndex: 'Status',
    key: 'Status',
  },
  {
    title: 'Run ID',
    dataIndex: 'Run_ID',
    key: 'Run_ID',
  },
  {
    title: 'Created Time',
    dataIndex: 'Created_Time',
    key: 'Created_Time',
  },
];

const dataSourceError = [
  {
    key: '',
    Occurrences: '',
    Created: '',
    Last_Seen: '',
    Message: '',
    Actions: '',
  }

];

const columnsError = [
  {
    title: 'Occurrences',
    dataIndex: 'Occurrences',
    key: 'Occurrences',
  },
  {
    title: 'Created',
    dataIndex: 'Created',
    key: 'Created',
  },
  {
    title: 'Last_Seen',
    dataIndex: 'Last_Seen',
    key: 'Last_Seen',
  },
  {
    title: 'Message',
    dataIndex: 'Message',
    key: 'Message',
  },
  {
    title: 'Actions',
    dataIndex: 'Actions',
    key: 'Actions',
  },
];

class JobDetail extends Component {

  constructor() {
    super();
    this.state = {
      overviewDataSource:[],
      createdAt:'',
      code:'',
      runCount:0,
      currentPage:1,
      total:50,
      minPayment: null,
      taskList:[],
    };
  }

  state = {
    tabPosition: 'top',
  };

  onChange = (pageNumber) => {
    this.getRuns(pageNumber.current);
    this.setState({currentPage:pageNumber.current})
  }

  onChangeError = (pageNumber) => {
    console.log('Page: ', pageNumber);
  }

  componentDidMount() {
    this.getJob();
    this.getRuns(1);
    this.setState({path: this.props.location.pathname.split('/')[2]});
  }

  getJob = () => {
    let id = this.props.location.pathname.split('/')[2];
    xhr.get(API_URL+"/job/specs/"+id).then((result) => {
      let data = result.data.data;
      this.setState({
        createdAt:data.createdAt,
        code:JSON.parse(data.params)
      });
      var formattedCode = JSON.stringify(JSON.parse(data.params), null, 2);
      this.setState({formattedCode});
      this.setState({minPayment:data.minPayment});
    })
  }

  getRuns = (page) => {
    let id = this.props.location.pathname.split('/')[2];
    xhr.get(API_URL+"/job/runs?page="+page+"&size=10&id="+id).then((result) => {
      let data = result.data.data;
      let dataSource = [];
      data.forEach((item, index) => {
        dataSource[index] = {
          key: index,
          Status: status[item.status],
          Run_ID: item.id,
          Created_Time: item.createdAt,
          taskRuns: item.taskRuns
        }
      })
      this.setState({overviewDataSource:dataSource, runCount:result.data.count, total: result.data.count});
    })
  }

  delete = () => {
    let id = this.props.location.pathname.split('/')[2];
    xhr.delete(API_URL+"/job/specs/"+id).then((result) => {
      console.log(result);
      if (result.error) {
        this.error(result.error)
      }
      if(result.data.msg=='success'){
        this.success()
      } else{
        this.error(result.data.msg)
      }
    }).catch((e) => {
      this.error(e.toString())
    });
  }

  getRunDetail = (task) => {
    this.setState({taskList:task})
  }

  duplicate = () => {
    let {code} = this.state;
    this.props.history.push({ pathname: "/jobs", state: { code:JSON.stringify(code), create:true } })
  }

  copy = () => {}

  success = () => {
    Modal.success({
      content: 'Successful!',
    });
  }

  error = (message) => {
    Modal.error({
      content: message,
    });
  }

  render() {
    let {path, formattedCode, createdAt, runCount, code, taskList, overviewDataSource, currentPage, total, minPayment} = this.state;

    return <Fragment>

      <PageHeader title="Job Details"/>


      <div>
        <div style={{marginBottom: 16}}>
          {path}
        </div>
        <div>
          Created at {createdAt}

          <div style={{float:'right',marginLeft:'10px'}}>
            <Button onClick={this.delete}>Archive</Button>
          </div>
          <div style={{float:'right',marginLeft:'10px'}}>
            <Button onClick={this.duplicate}>Duplicate</Button>
          </div>
          <div style={{float:'right',marginLeft:'10px'}}>
            <CopyToClipboard text={JSON.stringify(code)}>
              <Button onClick={this.copy}>Copy</Button>
            </CopyToClipboard>
          </div>

        </div>


        <Tabs tabPosition={this.state.tabPosition} animated={false} style={{marginTop: '30px'}}>
          <TabPane tab="Overview" key="1">

            <Row gutter={24}>
              <Col lg={18}>
                <Card title="">
                  <h3>Recent Job Runs</h3>
                  <Table dataSource={overviewDataSource}
                         columns={columns}
                         pagination={{
                           current: currentPage,
                           total: total,
                         }}
                         onChange={this.onChange}
                         onRow={record => {
                           return {
                             onClick: event => {
                               console.log(record);
                               this.getRunDetail(record.taskRuns)
                             },
                             onMouseEnter: (event) => {
                               $(event.target).css('cursor', 'pointer');
                             },
                           };
                         }}
                  />
                </Card>
              </Col>
              <Col lg={6}>

                <Card hoverable title="Task List" >

                  <Steps direction="vertical" progressDot >
                    {
                      taskList.map((item,index)=>(
                          <Step key={index} title={item.type} status={ant_status[item.status]} style={index!=(taskList.length-1) ? {paddingBottom:'20px'}:{}}/>
                      ))
                    }

                  </Steps>

                </Card>
                <Card hoverable title="Other" style={{marginTop: '16px'}}>
                  <table style={{width:'100%'}}>
                    <tbody>
                    <tr>
                      <td><p>Run Count</p></td>
                      <td><p>{runCount}</p></td>
                    </tr>
                    <tr>
                      <td><p>Initiator</p></td>
                      <td></td>
                    </tr>
                    <tr>
                      <td><p>Minimum pay</p></td>
                      <td><p>{minPayment==null?'Null':minPayment}</p></td>
                    </tr>
                    </tbody>
                  </table>
                </Card>
              </Col>
            </Row>

          </TabPane>
          <TabPane tab="Code" key="2">
            <Row gutter={24}>
              <Col lg={24}>
                <Card title="">
                  <h3>Definition</h3>
                  <hr/>
                  <pre id="geoJsonTxt">{formattedCode}</pre>

                </Card>
              </Col>
            </Row>
          </TabPane>
          <TabPane tab="Errors" key="3">
            <Row>


            </Row>
          </TabPane>
        </Tabs>
      </div>

      <Row gutter={16}>


      </Row>


    </Fragment>
  }
}

export default JobDetail;