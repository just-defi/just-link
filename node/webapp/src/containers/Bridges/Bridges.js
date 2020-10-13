import React, {Component, Fragment} from 'react';
import {Table, Row, PageHeader, Card, Button, Modal, Input} from 'antd';

const dataSource = [];

const columns = [];


class Bridges extends Component {

  constructor() {
    super();
    this.state = {

    };
  }


  componentDidMount() {

  }

  onChange = (pageNumber) => {
    console.log('Page: ', pageNumber);
  }


  render() {

    return <Fragment>

      <PageHeader title="Bridges"/>

      <Row gutter={16}>

        <Table dataSource={dataSource} columns={columns} onChange={this.onChange}/>


      </Row>



    </Fragment>
  }
}

export default Bridges;