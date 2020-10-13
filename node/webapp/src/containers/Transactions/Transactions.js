import React, {Component, Fragment} from 'react';
import {Table, Row, PageHeader, Card, Button, Modal, Input} from 'antd';

const dataSource = [
  {
    key: '1',
    Hash: 'Mike',
    From: 32,
    To: '10 Downing Street',
    Nonce :'11'
  },
  {
    key: '2',
    Hash: 'Mike',
    From: 32,
    To: '10 Downing Street',
    Nonce :'11'
  },

];

const columns = [
  {
    title: 'Hash',
    dataIndex: 'Hash',
    key: 'Hash',
  },
  {
    title: 'From',
    dataIndex: 'From',
    key: 'From',
  },
  {
    title: 'To',
    dataIndex: 'To',
    key: 'To',
  },
  {
    title: 'Nonce',
    dataIndex: 'Nonce',
    key: 'Nonce',
  },
];


class Transactions extends Component {

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

      <PageHeader title="Transactions"/>

      <Row gutter={16}>

        <Table dataSource={dataSource} columns={columns} onChange={this.onChange}/>


      </Row>



    </Fragment>
  }
}

export default Transactions;