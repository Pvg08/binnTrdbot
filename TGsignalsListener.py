import sys
import time
import datetime
import re
import random
import configparser

from telethon import utils, events, TelegramClient, ConnectionMode
from telethon.tl.functions.channels import GetFullChannelRequest
from telethon.tl.types import InputChannel, PeerChat
from telethon.tl.functions.contacts import ResolveUsernameRequest
from telethon.tl.functions.messages import CheckChatInviteRequest
from telethon.extensions import markdown

class ChannelBlock(object):
    def __init__(self):
        self.name = ''
        self.title = ''
        self.id = 0
        self.entity = None

class RegBlock(object):
    def __init__(self):
        self.name = ''
        self.title = ''
        self.rating = 0
        self.reg_has = None
        self.reg_skip = None
        self.reg_coin = None
        self.reg_coin2 = None
        self.reg_price_from = None
        self.reg_price_to = None
        self.reg_price_target = None

class SignalRow(object):
    def __init__(self):
        self.title = ''
        self.coin = None
        self.coin2 = None
        self.price_from = None
        self.price_to = None
        self.price_target = None
        self.rating = None
        self.date = None
        self.index = None
        self.sort = 0.0

    def init_sort(self):
        self.sort = 0.0
        if self.price_from:
            self.sort = self.sort + 1.0
        if self.price_to:
            self.sort = self.sort + 1.0
        if self.price_target:
            self.sort = self.sort + 1.0
        if self.coin2:
            self.sort = self.sort + 0.75
        self.sort = self.sort + self.rating / 20.0

    def __repr__(self):
        return str(self.rating) + ";" + self.date + ";" + self.coin + ";" + self.coin2 + ";" + self.price_from + ";" + self.price_to + ";" + self.price_target + ';' + str(self.index) + ";" + str(self.title)

def enable_win_unicode_console():
    if sys.version_info >= (3, 6):
        return
    import win_unicode_console
    win_unicode_console.enable() 

def getFirstMatch(regex, txt):
    if regex:
        matches = re.finditer(regex, txt, re.UNICODE | re.IGNORECASE | re.MULTILINE)
        for matchNum, match in enumerate(matches):
            for groupNum in range(0, len(match.groups())):
                return match.group(1)
    return ''

def dateFix(date):
    ts = time.time()
    utc_offset = (datetime.datetime.fromtimestamp(ts) - datetime.datetime.utcfromtimestamp(ts)).total_seconds()
    datetime_obj_naive = datetime.datetime.strptime(str(date), "%Y-%m-%d %H:%M:%S")
    datetime_obj_naive = datetime_obj_naive + datetime.timedelta(seconds = utc_offset)
    return str(datetime_obj_naive)

def numFix(txt):
    if txt:
        txt = txt.replace(',', '.').strip(',.+ \n\t')
        if txt.count('.') > 1:
            rtxt = getFirstMatch(r'^(0\.[0-9]{2,12})', txt)
            if not rtxt:
                rtxt = getFirstMatch(r'^([0-9]{2,12})', txt)
            if not rtxt:
                rtxt = float(getFirstMatch(r'^([0-9.]{2,12})', txt))
                rtxt = str(rtxt).replace(',', '.')
            txt = rtxt
    if txt and (txt.find('k')>=0 or txt.find('K')>=0):
        num = float(getFirstMatch(r'^([0-9.]{2,12})', txt))
        num = num * 1000
        txt = str(num)
    return txt

def separateCheck(txt, title, date, client):
    if (txt is None) or (txt == ''):
        return
    non_bmp_map = dict.fromkeys(range(0x10000, sys.maxunicode + 1), 0xfffd)
    txt = txt.translate(non_bmp_map)
    title = title.translate(non_bmp_map).replace(';', ' ')
    global signal_separator
    if signal_separator:
        parts = re.split(signal_separator, txt, flags = re.UNICODE | re.IGNORECASE | re.MULTILINE)
        if parts and len(parts) > 0:
            i = 0
            while i < len(parts):
                textCheck(parts[i], title, date, client)
                i = i + 1
        else:
            textCheck(txt, title, date, client)
    else:
        textCheck(txt, title, date, client)
    return

def textCheck(txt, title, date, client):
    global signal_expr

    #print(txt)
    #print("_________________")

    stop_coin_names = [
        'BTC', 
        'BINANCE', 
        'BITTREX', 
        'YOBIT', 
        'CRYPTOPIA', 
        'SIGNAL', 
        'ZONE', 
        'UPDATE', 
        'PRICE', 
        'COIN', 
        'VIP', 
        'INSIDE', 
        'PRIVATE', 
        'AGAIN', 
        'RESULT', 
        'RESULTS', 
        'AROUND', 
        'NEAR', 
        'STOP', 
        'EARLY', 
        'MIDTERM', 
        'SHORTTERM', 
        'SHORT', 
        'LONGTERM', 
        'LONGTER', 
        'CRYPTOR', 
        'PART', 
        'OK', 
        'SOME'
    ]
    best_item = None

    i = 0
    while i < len(signal_expr):
        if signal_expr[i].reg_has:
            hasmatch = getFirstMatch(signal_expr[i].reg_has, txt)
            skipmatch = getFirstMatch(signal_expr[i].reg_skip, txt)
            if hasmatch and not skipmatch:
                coin = getFirstMatch(signal_expr[i].reg_coin, txt).upper()
                coin2 = getFirstMatch(signal_expr[i].reg_coin2, txt).upper()
                price_from = numFix(getFirstMatch(signal_expr[i].reg_price_from, txt))
                price_to = numFix(getFirstMatch(signal_expr[i].reg_price_to, txt))
                price_target = numFix(getFirstMatch(signal_expr[i].reg_price_target, txt))
                if coin and coin not in stop_coin_names and coin2 not in stop_coin_names and (price_from or price_to or price_target):
                    ri = SignalRow()
                    ri.title = title
                    ri.coin = coin
                    ri.coin2 = coin2
                    ri.price_from = price_from
                    ri.price_to = price_to
                    ri.price_target = price_target
                    ri.rating = signal_expr[i].rating
                    ri.date = date
                    ri.index = i + 1
                    ri.init_sort()
                    if best_item is None or best_item.sort < ri.sort:
                        best_item = ri
        i = i + 1

    if best_item is not None:
        #if best_item.coin == 'NANO':
        #    print(txt)
        print(str(best_item))

    return

def checkCurchan(channel, client, signals_limit):
    chantitle = channel.title
    if not chantitle:
        chantitle = channel.name
    if not chantitle:
        chantitle = str(channel.id)
    channame = channel.name
    if channel.id and channel.id > 0:
        int_chan = int(channel.id)
    else:
        int_chan = 0

    if int_chan != 0:
        try:
            input_channel =  client(GetFullChannelRequest(int_chan))
            channel_id = input_channel.full_chat.id
        except Exception as e:
            print(str(e))
            return None
    else:
        try:
            response = client.invoke(ResolveUsernameRequest(channame))
            channel_id = response.peer.channel_id
        except Exception as e:
            print(str(e))
            return None

    for msg in client.get_messages(channel_id, limit=signals_limit):
        if (msg != '') and not (msg is None):
            if not hasattr(msg, 'text'):
                msg.text = None
            if msg.text is None and hasattr(msg, 'entities'):
                msg.text = markdown.unparse(msg.message, msg.entities or [])
            if msg.text is None and msg.message:
                msg.text = msg.message
            separateCheck(msg.text, chantitle, dateFix(msg.date), client)

    return client.get_input_entity(channel_id)

def main():
    if not sys.argv[1] or not sys.argv[2] or not sys.argv[3]:
        return
    enable_win_unicode_console()

    global signal_expr
    global signal_separator
    signal_expr = []

    config = configparser.RawConfigParser(allow_no_value = True)
    config.read('signals_listener.ini')
    session_name = config['main']['session_fname']
    api_id = sys.argv[1]
    api_hash = sys.argv[2]
    phone = sys.argv[3]

    chancount = int(config['channels']['count'])
    signals_preload = int(config['channels']['signals_preload'])
    signal_separator = str(config['channels']['signal_separator'])
    channels = []

    chan_index = 1
    cname = ''
    cid = 0
    last_cname = ''
    last_cid = 0
    while chan_index <= chancount:
        channel_config = config['channel_' + str(chan_index)]

        expr_b = RegBlock()
        expr_b.rating = int(channel_config['signal_rating'])
        expr_b.reg_has = channel_config['signal_has']
        expr_b.reg_skip = channel_config['signal_skip']
        expr_b.reg_coin = channel_config['signal_coin']
        expr_b.reg_coin2 = channel_config['signal_coin2']
        expr_b.reg_price_from = channel_config['signal_price_from']
        expr_b.reg_price_to = channel_config['signal_price_to']
        expr_b.reg_price_target = channel_config['signal_price_target']
        if not expr_b.reg_has:
            expr_b.reg_has = expr_b.reg_coin

        if cname or cid:
            last_cname = cname
            last_cid = cid
        try:
            cname = channel_config['name']
        except:
            cname = ''
        try:
            cid = channel_config['id']
        except:
            cid = 0

        if cname or cid:
            newchan = ChannelBlock()
            newchan.name = cname
            newchan.id = int(cid)
            newchan.title = channel_config['title']
            channels.append(newchan)
            expr_b.name = cname
            expr_b.cid = cid
        else:
            expr_b.name = last_cname
            expr_b.cid = last_cid

        signal_expr.append(expr_b)
        chan_index = chan_index + 1

    client = TelegramClient(
        session_name,
        api_id=api_id,
        api_hash=api_hash,
        connection_mode=ConnectionMode.TCP_ABRIDGED,
        proxy=None,
        update_workers=1,
        spawn_read_thread=True
    )

    print('Connecting to Telegram servers...')
    if not client.connect():
        print('Initial connection failed. Retrying...')
        if not client.connect():
            print('Could not connect to Telegram servers.')
            return

    if not client.is_user_authorized():
        client.send_code_request(phone)
        print('Enter the code ('+phone+'): ')
        code = input()
        client.sign_in(phone, code)

    print(client.session.server_address)   # Successfull

    chan_index = 0
    while chan_index < len(channels):
        print(channels[chan_index].title)
        channels[chan_index].entity = checkCurchan(channels[chan_index], client, signals_preload)
        if channels[chan_index].entity:
            @client.on(events.NewMessage(chats=[channels[chan_index].entity], incoming=True))
            def normal_handler(event):
                separateCheck(event.text, '-', dateFix(event.message.date), client)
        time.sleep(1)
        chan_index = chan_index + 1

    print('------------------')

    while True:
        time.sleep(0.1)

if __name__ == '__main__':
    main()
